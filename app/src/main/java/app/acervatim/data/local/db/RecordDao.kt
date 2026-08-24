package app.acervatim.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sync.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface RecordDao {

    /**
     * Renvoie toutes les paires (disque × artiste) correspondant au filtre, dans l'ordre du tri
     * demandé. La requête elle-même est construite par `RecordQueries.filteredJoinRows` : la
     * clause `ORDER BY` dépend du tri choisi, ce qu'une `@Query` figée ne sait pas exprimer.
     *
     * `observedEntities` est ce qui rend le `Flow` réactif : une requête brute étant opaque pour
     * Room, il faut lui dire quelles tables surveiller, sinon la liste ne se rafraîchirait plus
     * après un ajout ou une écoute.
     *
     * `collectionTypeFilter` null = toutes collections confondues (rare ; utilisé pour les
     * statistiques globales). En usage courant on filtre toujours sur la collection active.
     */
    @RawQuery(
        observedEntities = [
            RecordEntity::class,
            ArtistEntity::class,
            RecordArtistCrossRef::class
        ]
    )
    fun observeFilteredJoinRows(query: SupportSQLiteQuery): Flow<List<RecordWithArtistsRow>>

    @Query(
        """
        SELECT r.*,
               a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
        FROM records r
        INNER JOIN record_artists ra ON ra.recordId = r.id
        INNER JOIN artists a ON a.id = ra.artistId
        WHERE r.id = :id
        ORDER BY ra.position
        """
    )
    fun observeByIdJoinRows(id: String): Flow<List<RecordWithArtistsRow>>

    @Query(
        """
        SELECT r.*,
               a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
        FROM records r
        INNER JOIN record_artists ra ON ra.recordId = r.id
        INNER JOIN artists a ON a.id = ra.artistId
        WHERE r.seriesId = :seriesId
        ORDER BY r.volumeNumber, r.id, ra.position
        """
    )
    fun observeBySeriesJoinRows(seriesId: String): Flow<List<RecordWithArtistsRow>>

    @Query(
        """
        SELECT r.*,
               a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
        FROM records r
        INNER JOIN record_artists ra ON ra.recordId = r.id
        INNER JOIN artists a ON a.id = ra.artistId
        WHERE r.barcode = :barcode
        ORDER BY ra.position
        """
    )
    suspend fun findByBarcodeJoinRows(barcode: String): List<RecordWithArtistsRow>

    @Query(
        """
        SELECT r.*,
               a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
        FROM records r
        INNER JOIN record_artists ra ON ra.recordId = r.id
        INNER JOIN artists a ON a.id = ra.artistId
        WHERE r.isbn = :isbn
        ORDER BY ra.position
        """
    )
    suspend fun findByIsbnJoinRows(isbn: String): List<RecordWithArtistsRow>

    @Query(
        """
        SELECT DISTINCT genre FROM records
        WHERE genre IS NOT NULL AND TRIM(genre) != ''
        ORDER BY genre COLLATE NOCASE
        """
    )
    fun observeDistinctGenres(): Flow<List<String>>

    @Query(
        """
        SELECT COUNT(*) FROM records
        WHERE status = 'OWNED'
          AND (:collectionType IS NULL OR collectionType = :collectionType)
        """
    )
    fun observeOwnedCount(collectionType: CollectionType? = null): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM records
        WHERE status = 'WISHLIST'
          AND (:collectionType IS NULL OR collectionType = :collectionType)
        """
    )
    fun observeWishlistCount(collectionType: CollectionType? = null): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM records
        WHERE (:collectionType IS NULL OR collectionType = :collectionType)
        """
    )
    fun observeTotalCount(collectionType: CollectionType? = null): Flow<Int>

    @Upsert
    suspend fun upsertRecord(record: RecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: RecordArtistCrossRef)

    @Query("DELETE FROM record_artists WHERE recordId = :recordId")
    suspend fun deleteCrossRefsForRecord(recordId: String)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        SELECT r.*,
               a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
        FROM records r
        INNER JOIN record_artists ra ON ra.recordId = r.id
        INNER JOIN artists a ON a.id = ra.artistId
        ORDER BY r.id, ra.position
        """
    )
    suspend fun listAllJoinRows(): List<RecordWithArtistsRow>

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    // Les écritures partielles marquent la ligne DIRTY pour que la prochaine synchro la pousse.

    /**
     * Une écoute : horodate et incrémente dans la **même** écriture. Séparer les deux
     * laisserait passer un état où `lastPlayedAt` avance sans que le compteur suive.
     */
    @Query(
        """
        UPDATE records
        SET lastPlayedAt = :playedAt, playCount = playCount + 1, updatedAt = :updatedAt,
            syncState = 'DIRTY'
        WHERE id = :id
        """
    )
    suspend fun markPlayed(id: String, playedAt: Instant, updatedAt: Instant)

    /** Remise à zéro du geste d'écoute : ni date, ni compteur (ils vont par paire). */
    @Query(
        """
        UPDATE records
        SET lastPlayedAt = NULL, playCount = 0, updatedAt = :updatedAt, syncState = 'DIRTY'
        WHERE id = :id
        """
    )
    suspend fun resetPlayback(id: String, updatedAt: Instant)

    @Query("UPDATE records SET status = :status, updatedAt = :ts, syncState = 'DIRTY' WHERE id = :id")
    suspend fun updateStatus(id: String, status: ItemStatus, ts: Instant)

    @Query("UPDATE records SET rating = :rating, updatedAt = :ts, syncState = 'DIRTY' WHERE id = :id")
    suspend fun updateRating(id: String, rating: Int?, ts: Instant)

    // --- Synchronisation ---------------------------------------------------------------------

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun getEntityById(id: String): RecordEntity?

    @Query("SELECT * FROM records WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): RecordEntity?

    @Query("SELECT * FROM records WHERE barcode = :barcode LIMIT 1")
    suspend fun getEntityByBarcode(barcode: String): RecordEntity?

    @Query("SELECT * FROM records WHERE isbn = :isbn LIMIT 1")
    suspend fun getEntityByIsbn(isbn: String): RecordEntity?

    @Query("SELECT * FROM records WHERE syncState != 'SYNCED'")
    suspend fun listEntitiesNeedingPush(): List<RecordEntity>

    @Query(
        """
        SELECT r.*,
               a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
        FROM records r
        INNER JOIN record_artists ra ON ra.recordId = r.id
        INNER JOIN artists a ON a.id = ra.artistId
        WHERE r.id = :id
        ORDER BY ra.position
        """
    )
    suspend fun joinRowsById(id: String): List<RecordWithArtistsRow>

    @Query(
        "UPDATE records SET remoteId = :remoteId, serverUpdatedAt = :serverUpdatedAt, " +
            "syncState = :syncState WHERE id = :id"
    )
    suspend fun updateSync(id: String, remoteId: String?, serverUpdatedAt: Instant?, syncState: SyncState)

    /**
     * Remplace l'ensemble des artistes du disque par la liste fournie (ordre préservé).
     * Les `artists` doivent déjà exister en base.
     */
    @Transaction
    suspend fun replaceCrossRefs(recordId: String, artistIds: List<String>) {
        deleteCrossRefsForRecord(recordId)
        artistIds.forEachIndexed { index, artistId ->
            insertCrossRef(RecordArtistCrossRef(recordId, artistId, index))
        }
    }
}
