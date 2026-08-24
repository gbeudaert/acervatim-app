package app.acervatim.domain.repository

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RecordFilter
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

    /** Tous les items (tomes) d'une série, triés par numéro de volume. */
    fun observeBySeriesId(seriesId: String): Flow<List<Record>>

    fun observeDistinctGenres(): Flow<List<String>>

    fun observeDistinctArtistNames(): Flow<List<String>>

    /** `collectionType` null = toutes collections confondues. */
    fun observeOwnedCount(collectionType: CollectionType? = null): Flow<Int>

    fun observeWishlistCount(collectionType: CollectionType? = null): Flow<Int>

    fun observeTotalCount(collectionType: CollectionType? = null): Flow<Int>

    suspend fun findByBarcode(barcode: String): Record?

    /** Renvoie le tome / livre portant cet ISBN (forme normalisée), null sinon. */
    suspend fun findByIsbn(isbn: String): Record?

    /** Snapshot non-réactif de tous les disques (utilisé par l'export backup). */
    suspend fun listAll(): List<Record>

    suspend fun upsert(record: Record)

    suspend fun delete(record: Record)

    suspend fun deleteById(id: String)

    /** Supprime tous les disques + cross-refs artistes + artistes orphelins. Utilisé par l'import backup. */
    suspend fun deleteAll()

    /**
     * Enregistre une écoute : `lastPlayedAt` = [at] **et** `playCount` incrémenté d'un.
     * Les deux décrivent le même geste, d'où une seule opération.
     */
    suspend fun markPlayed(id: String, at: Instant)

    /** Annule l'historique d'écoute d'un item : date effacée et compteur remis à zéro. */
    suspend fun resetPlayback(id: String)

    suspend fun updateStatus(id: String, status: ItemStatus, ts: Instant)

    suspend fun updateRating(id: String, rating: Int?, ts: Instant)
}
