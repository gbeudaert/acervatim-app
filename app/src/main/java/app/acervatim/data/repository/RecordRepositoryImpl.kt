package app.acervatim.data.repository

import androidx.room.withTransaction
import app.acervatim.data.local.db.AcervatimDatabase
import app.acervatim.data.local.db.ArtistDao
import app.acervatim.data.local.db.ArtistEntity
import app.acervatim.data.local.db.RecordDao
import app.acervatim.data.local.db.groupToRecords
import app.acervatim.data.local.db.toEntity
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.normalizeArtistName
import app.acervatim.domain.model.sync.SyncState
import app.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val database: AcervatimDatabase,
    private val recordDao: RecordDao,
    private val artistDao: ArtistDao,
    private val collectionResolver: LocalCollectionResolver,
    private val clock: Clock
) : RecordRepository {

    private fun rawFlow(filter: RecordFilter) =
        recordDao.observeFilteredJoinRows(
            query = filter.query.trim(),
            collectionTypeFilter = filter.collectionType,
            statusFilter = filter.status.toItemStatus(),
            genreFilter = filter.genre,
            formatFilter = filter.format,
            notPlayedSince = notPlayedSinceCutoff(filter)
        )

    override fun observeAll(): Flow<List<Record>> = observeFiltered(RecordFilter())

    override fun observeFiltered(filter: RecordFilter): Flow<List<Record>> =
        rawFlow(filter).map { rows ->
            rows.groupToRecords().sortedWith(RECORD_BY_PRIMARY_ARTIST)
        }

    override fun observeFlattenedByArtist(filter: RecordFilter): Flow<List<RecordArtistEntry>> =
        rawFlow(filter).map { rows ->
            val records = rows.groupToRecords()
            records
                .flatMap { record ->
                    record.artists.map { artist ->
                        RecordArtistEntry(
                            record = record,
                            displayedArtist = artist,
                            featuringArtists = emptyList()
                        )
                    }
                }
                .sortedWith(ENTRY_BY_DISPLAYED_ARTIST)
        }

    override fun observeByPrimaryArtist(filter: RecordFilter): Flow<List<RecordArtistEntry>> =
        rawFlow(filter).map { rows ->
            rows.groupToRecords()
                .mapNotNull { record ->
                    val primary = record.primaryArtist ?: return@mapNotNull null
                    RecordArtistEntry(
                        record = record,
                        displayedArtist = primary,
                        featuringArtists = record.featuringArtists
                    )
                }
                .sortedWith(ENTRY_BY_DISPLAYED_ARTIST)
        }

    override fun observeById(id: String): Flow<Record?> =
        recordDao.observeByIdJoinRows(id).map { rows ->
            rows.groupToRecords().firstOrNull()
        }

    override fun observeBySeriesId(seriesId: String): Flow<List<Record>> =
        recordDao.observeBySeriesJoinRows(seriesId).map { rows ->
            rows.groupToRecords().sortedWith(RECORD_BY_VOLUME)
        }

    override fun observeDistinctGenres(): Flow<List<String>> = recordDao.observeDistinctGenres()

    override fun observeDistinctArtistNames(): Flow<List<String>> = artistDao.observeAllNames()

    override fun observeOwnedCount(collectionType: CollectionType?): Flow<Int> =
        recordDao.observeOwnedCount(collectionType)

    override fun observeWishlistCount(collectionType: CollectionType?): Flow<Int> =
        recordDao.observeWishlistCount(collectionType)

    override fun observeTotalCount(collectionType: CollectionType?): Flow<Int> =
        recordDao.observeTotalCount(collectionType)

    override suspend fun findByBarcode(barcode: String): Record? =
        recordDao.findByBarcodeJoinRows(barcode).groupToRecords().firstOrNull()

    override suspend fun findByIsbn(isbn: String): Record? =
        recordDao.findByIsbnJoinRows(isbn).groupToRecords().firstOrNull()

    override suspend fun listAll(): List<Record> =
        recordDao.listAllJoinRows().groupToRecords()

    override suspend fun upsert(record: Record) {
        require(record.artists.isNotEmpty()) { "a record must have at least one artist" }
        database.withTransaction {
            val collectionId = collectionResolver.collectionIdFor(record.collectionType)
            val existing = recordDao.getEntityById(record.id)
            // remoteId présent ⇒ déjà sur le serveur ⇒ l'édition est DIRTY (PATCH) ;
            // sinon LOCAL_ONLY (POST). On préserve remoteId / serverUpdatedAt existants.
            val syncState = if (existing?.remoteId != null) SyncState.DIRTY else SyncState.LOCAL_ONLY
            recordDao.upsertRecord(
                record.toEntity(
                    collectionId = existing?.collectionId?.takeIf { it.isNotBlank() } ?: collectionId,
                    remoteId = existing?.remoteId,
                    syncState = syncState,
                    serverUpdatedAt = existing?.serverUpdatedAt
                )
            )
            val artistIds = record.artists.map { input ->
                resolveOrCreateArtist(input.name)
            }
            recordDao.replaceCrossRefs(record.id, artistIds)
            artistDao.deleteUnused()
        }
    }

    private suspend fun resolveOrCreateArtist(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "artist name must not be blank" }
        val normalized = normalizeArtistName(trimmed)
        val existing = artistDao.findByNormalizedName(normalized)
        if (existing != null) return existing.id
        val entity = ArtistEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            normalizedName = normalized
        )
        artistDao.upsert(entity)
        return entity.id
    }

    override suspend fun delete(record: Record) {
        database.withTransaction {
            recordDao.deleteById(record.id)
            artistDao.deleteUnused()
        }
    }

    override suspend fun deleteById(id: String) {
        database.withTransaction {
            recordDao.deleteById(id)
            artistDao.deleteUnused()
        }
    }

    override suspend fun deleteAll() {
        database.withTransaction {
            recordDao.deleteAll()
            artistDao.deleteUnused()
        }
    }

    override suspend fun updateLastPlayed(id: String, ts: Instant?) {
        recordDao.updateLastPlayed(id, lastPlayedAt = ts, updatedAt = clock.instant())
    }

    override suspend fun updateStatus(id: String, status: ItemStatus, ts: Instant) {
        recordDao.updateStatus(id, status, ts)
    }

    override suspend fun updateRating(id: String, rating: Int?, ts: Instant) {
        recordDao.updateRating(id, rating, ts)
    }

    private fun notPlayedSinceCutoff(filter: RecordFilter): Instant? =
        if (filter.notPlayedSinceSixMonths) {
            clock.instant().minus(SIX_MONTHS_DAYS, ChronoUnit.DAYS)
        } else null

    private companion object {
        const val SIX_MONTHS_DAYS: Long = 183L

        val RECORD_BY_PRIMARY_ARTIST: Comparator<Record> = compareBy(
            { it.primaryArtist?.name?.lowercase().orEmpty() },
            { it.title.lowercase() }
        )

        val ENTRY_BY_DISPLAYED_ARTIST: Comparator<RecordArtistEntry> = compareBy(
            { it.displayedArtist.name.lowercase() },
            { it.record.title.lowercase() }
        )

        val RECORD_BY_VOLUME: Comparator<Record> = compareBy(
            { it.volumeNumber ?: Int.MAX_VALUE },
            { it.title.lowercase() }
        )
    }
}
