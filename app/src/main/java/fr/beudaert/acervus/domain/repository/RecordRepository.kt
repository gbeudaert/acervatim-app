package fr.beudaert.acervus.domain.repository

import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordArtistEntry
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.model.RecordStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface RecordRepository {

    fun observeAll(): Flow<List<Record>>

    fun observeFiltered(filter: RecordFilter): Flow<List<Record>>

    /** Mode ON ("duplicate"): un disque à N artistes apparaît N fois. */
    fun observeFlattenedByArtist(filter: RecordFilter): Flow<List<RecordArtistEntry>>

    /** Mode OFF ("primary"): un disque par ligne, sous son artiste principal. */
    fun observeByPrimaryArtist(filter: RecordFilter): Flow<List<RecordArtistEntry>>

    fun observeById(id: String): Flow<Record?>

    fun observeDistinctGenres(): Flow<List<String>>

    fun observeDistinctArtistNames(): Flow<List<String>>

    fun observeOwnedCount(): Flow<Int>

    fun observeWishlistCount(): Flow<Int>

    suspend fun findByBarcode(barcode: String): Record?

    suspend fun upsert(record: Record)

    suspend fun delete(record: Record)

    suspend fun deleteById(id: String)

    suspend fun updateLastPlayed(id: String, ts: Instant?)

    suspend fun updateStatus(id: String, status: RecordStatus, ts: Instant)

    suspend fun updateRating(id: String, rating: Int?, ts: Instant)
}
