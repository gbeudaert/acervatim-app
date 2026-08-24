package app.acervatim.domain

import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.recordComparator
import app.acervatim.domain.model.toEntriesByPrimaryArtist
import app.acervatim.domain.model.toEntriesFlattenedByArtist
import app.acervatim.domain.model.normalizeArtistName
import app.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID

class FakeRecordRepository : RecordRepository {

    private val state = MutableStateFlow<List<Record>>(emptyList())

    override fun observeAll(): Flow<List<Record>> = state.asStateFlow()

    /**
     * Même ordre que la vraie implémentation, obtenu par les **mêmes** comparateurs : un faux
     * qui trierait à sa façon validerait un ordre que l'app ne produit jamais.
     */
    override fun observeFiltered(filter: RecordFilter, sort: RecordSort): Flow<List<Record>> =
        state.map { list -> list.filter(matches(filter)).sortedWith(recordComparator(sort)) }

    override fun observeFlattenedByArtist(
        filter: RecordFilter,
        sort: RecordSort
    ): Flow<List<RecordArtistEntry>> =
        observeFiltered(filter, sort).map { it.toEntriesFlattenedByArtist(sort) }

    override fun observeByPrimaryArtist(
        filter: RecordFilter,
        sort: RecordSort
    ): Flow<List<RecordArtistEntry>> =
        observeFiltered(filter, sort).map { it.toEntriesByPrimaryArtist() }

    override fun observeById(id: String): Flow<Record?> =
        state.map { list -> list.firstOrNull { it.id == id } }

    override fun observeBySeriesId(seriesId: String): Flow<List<Record>> =
        state.map { list ->
            list.filter { it.seriesId == seriesId }
                .sortedWith(compareBy({ it.volumeNumber ?: Int.MAX_VALUE }, { it.title.lowercase() }))
        }

    override fun observeDistinctGenres(): Flow<List<String>> =
        state.map { list ->
            list.mapNotNull { it.genre?.trim()?.takeIf { g -> g.isNotEmpty() } }
                .distinct()
                .sortedBy { it.lowercase() }
        }

    override fun observeDistinctArtistNames(): Flow<List<String>> =
        state.map { list ->
            list.flatMap { record -> record.artists.map { it.name } }
                .distinctBy { normalizeArtistName(it) }
                .sortedBy { it.lowercase() }
        }

    override fun observeOwnedCount(collectionType: CollectionType?): Flow<Int> =
        state.map { list ->
            list.count { r ->
                r.status == ItemStatus.OWNED && (collectionType == null || r.collectionType == collectionType)
            }
        }

    override fun observeWishlistCount(collectionType: CollectionType?): Flow<Int> =
        state.map { list ->
            list.count { r ->
                r.status == ItemStatus.WISHLIST && (collectionType == null || r.collectionType == collectionType)
            }
        }

    override fun observeTotalCount(collectionType: CollectionType?): Flow<Int> =
        state.map { list ->
            list.count { r -> collectionType == null || r.collectionType == collectionType }
        }

    override suspend fun findByBarcode(barcode: String): Record? =
        state.value.firstOrNull { it.barcode == barcode }

    override suspend fun findByIsbn(isbn: String): Record? =
        state.value.firstOrNull { it.isbn == isbn }

    override suspend fun listAll(): List<Record> = state.value

    override suspend fun upsert(record: Record) {
        // Normalize artist ids: reuse existing ids by normalized_name, else generate.
        val canonicalArtists = record.artists.map { input ->
            val normalized = normalizeArtistName(input.name)
            val existingId = state.value
                .flatMap { it.artists }
                .firstOrNull { normalizeArtistName(it.name) == normalized }
                ?.id
            Artist(
                id = if (input.id.isNotBlank()) input.id else existingId ?: UUID.randomUUID().toString(),
                name = input.name.trim()
            )
        }
        val canonical = record.copy(artists = canonicalArtists)
        state.update { current ->
            val existing = current.indexOfFirst { it.id == canonical.id }
            if (existing >= 0) current.toMutableList().apply { set(existing, canonical) }
            else current + canonical
        }
    }

    override suspend fun delete(record: Record) {
        state.update { current -> current.filterNot { it.id == record.id } }
    }

    override suspend fun deleteById(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }

    override suspend fun markPlayed(id: String, at: Instant) {
        state.update { current ->
            current.map {
                if (it.id == id) {
                    it.copy(lastPlayedAt = at, playCount = it.playCount + 1, updatedAt = Instant.now())
                } else {
                    it
                }
            }
        }
    }

    override suspend fun resetPlayback(id: String) {
        state.update { current ->
            current.map {
                if (it.id == id) it.copy(lastPlayedAt = null, playCount = 0, updatedAt = Instant.now()) else it
            }
        }
    }

    override suspend fun updateStatus(id: String, status: ItemStatus, ts: Instant) {
        state.update { current ->
            current.map { if (it.id == id) it.copy(status = status, updatedAt = ts) else it }
        }
    }

    override suspend fun updateRating(id: String, rating: Int?, ts: Instant) {
        state.update { current ->
            current.map { if (it.id == id) it.copy(rating = rating, updatedAt = ts) else it }
        }
    }

    private fun matches(filter: RecordFilter): (Record) -> Boolean = { r ->
        val q = filter.query.trim()
        val queryMatches = q.isEmpty() ||
                r.title.contains(q, ignoreCase = true) ||
                r.artists.any { it.name.contains(q, ignoreCase = true) }
        queryMatches &&
                (filter.collectionType?.let { it == r.collectionType } ?: true) &&
                (filter.status.toItemStatus()?.let { it == r.status } ?: true) &&
                (filter.genre?.let { it == r.genre } ?: true) &&
                (filter.format?.let { it == r.format } ?: true)
    }
}
