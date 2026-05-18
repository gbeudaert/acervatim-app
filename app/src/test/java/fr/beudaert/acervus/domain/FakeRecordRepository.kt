package fr.beudaert.acervus.domain

import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordArtistEntry
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.model.RecordStatus
import fr.beudaert.acervus.domain.model.normalizeArtistName
import fr.beudaert.acervus.domain.repository.RecordRepository
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

    override fun observeFiltered(filter: RecordFilter): Flow<List<Record>> =
        state.map { list ->
            list.asSequence()
                .filter(matches(filter))
                .sortedWith(compareBy(
                    { it.primaryArtist?.name?.lowercase().orEmpty() },
                    { it.title.lowercase() }
                ))
                .toList()
        }

    override fun observeFlattenedByArtist(filter: RecordFilter): Flow<List<RecordArtistEntry>> =
        state.map { list ->
            list.asSequence()
                .filter(matches(filter))
                .flatMap { record ->
                    record.artists.asSequence().map { artist ->
                        RecordArtistEntry(record, artist, featuringArtists = emptyList())
                    }
                }
                .sortedWith(compareBy(
                    { it.displayedArtist.name.lowercase() },
                    { it.record.title.lowercase() }
                ))
                .toList()
        }

    override fun observeByPrimaryArtist(filter: RecordFilter): Flow<List<RecordArtistEntry>> =
        state.map { list ->
            list.asSequence()
                .filter(matches(filter))
                .mapNotNull { record ->
                    val primary = record.primaryArtist ?: return@mapNotNull null
                    RecordArtistEntry(
                        record = record,
                        displayedArtist = primary,
                        featuringArtists = record.featuringArtists
                    )
                }
                .sortedWith(compareBy(
                    { it.displayedArtist.name.lowercase() },
                    { it.record.title.lowercase() }
                ))
                .toList()
        }

    override fun observeById(id: String): Flow<Record?> =
        state.map { list -> list.firstOrNull { it.id == id } }

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

    override fun observeOwnedCount(): Flow<Int> =
        state.map { list -> list.count { it.status == RecordStatus.OWNED } }

    override fun observeWishlistCount(): Flow<Int> =
        state.map { list -> list.count { it.status == RecordStatus.WISHLIST } }

    override suspend fun findByBarcode(barcode: String): Record? =
        state.value.firstOrNull { it.barcode == barcode }

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

    override suspend fun updateLastPlayed(id: String, ts: Instant?) {
        state.update { current ->
            current.map {
                if (it.id == id) it.copy(lastPlayedAt = ts, updatedAt = ts ?: it.updatedAt) else it
            }
        }
    }

    override suspend fun updateStatus(id: String, status: RecordStatus, ts: Instant) {
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
                (filter.status.toRecordStatus()?.let { it == r.status } ?: true) &&
                (filter.genre?.let { it == r.genre } ?: true) &&
                (filter.format?.let { it == r.format } ?: true)
    }
}
