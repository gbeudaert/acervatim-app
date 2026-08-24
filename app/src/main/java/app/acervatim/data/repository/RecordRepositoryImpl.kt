package app.acervatim.data.repository

import androidx.room.withTransaction
import app.acervatim.data.local.db.AcervatimDatabase
import app.acervatim.data.local.db.ArtistDao
import app.acervatim.data.local.db.ArtistEntity
import app.acervatim.data.local.db.RecordDao
import app.acervatim.data.local.db.RecordQueries
import app.acervatim.data.local.db.groupToRecords
import app.acervatim.data.local.db.toEntity
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RECORD_BY_VOLUME
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.toEntriesByPrimaryArtist
import app.acervatim.domain.model.toEntriesFlattenedByArtist
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

    private fun rawFlow(filter: RecordFilter, sort: RecordSort) =
        recordDao.observeFilteredJoinRows(
            RecordQueries.filteredJoinRows(
                filter = filter,
                sort = sort,
                notPlayedSince = notPlayedSinceCutoff(filter)
            )
        )

    override fun observeAll(): Flow<List<Record>> = observeFiltered(RecordFilter())

    /**
     * Aucun `sortedWith` ici : l'ordre vient de l'`ORDER BY` de la requête et `groupToRecords`
     * le conserve. Retrier en mémoire supposerait de charger toute la collection avant de
     * pouvoir en afficher la première ligne.
     */
    override fun observeFiltered(filter: RecordFilter, sort: RecordSort): Flow<List<Record>> =
        rawFlow(filter, sort).map { rows -> rows.groupToRecords() }

    override fun observeFlattenedByArtist(
        filter: RecordFilter,
        sort: RecordSort
    ): Flow<List<RecordArtistEntry>> =
        rawFlow(filter, sort).map { rows ->
            rows.groupToRecords().toEntriesFlattenedByArtist(sort)
        }

    override fun observeByPrimaryArtist(
        filter: RecordFilter,
        sort: RecordSort
    ): Flow<List<RecordArtistEntry>> =
        rawFlow(filter, sort).map { rows -> rows.groupToRecords().toEntriesByPrimaryArtist() }

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

    override suspend fun markPlayed(id: String, at: Instant) {
        recordDao.markPlayed(id, playedAt = at, updatedAt = clock.instant())
    }

    override suspend fun resetPlayback(id: String) {
        recordDao.resetPlayback(id, updatedAt = clock.instant())
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
    }
}
