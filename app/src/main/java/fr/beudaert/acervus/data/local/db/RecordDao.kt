package fr.beudaert.acervus.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ItemStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface RecordDao {

    /**
     * Renvoie toutes les paires (disque × artiste) correspondant au filtre.
     * Le repository regroupe par disque pour reconstruire le modèle domaine.
     *
     * `collectionTypeFilter` null = toutes collections confondues (rare ; utilisé pour les
     * statistiques globales). En usage courant on filtre toujours sur la collection active.
     */
    @Query(
        """
        SELECT r.*,
               a.id AS artist_id, a.name AS artist_name, ra.position AS artist_position
        FROM records r
        INNER JOIN record_artists ra ON ra.recordId = r.id
        INNER JOIN artists a ON a.id = ra.artistId
        WHERE (
                :query = ''
                OR r.title LIKE '%' || :query || '%'
                OR EXISTS (
                    SELECT 1 FROM record_artists ra2
                    INNER JOIN artists a2 ON a2.id = ra2.artistId
                    WHERE ra2.recordId = r.id AND a2.name LIKE '%' || :query || '%'
                )
            )
          AND (:collectionTypeFilter IS NULL OR r.collectionType = :collectionTypeFilter)
          AND (:statusFilter IS NULL OR r.status = :statusFilter)
          AND (:genreFilter IS NULL OR r.genre = :genreFilter)
          AND (:formatFilter IS NULL OR r.format = :formatFilter)
          AND (:notPlayedSince IS NULL OR r.lastPlayedAt IS NULL OR r.lastPlayedAt < :notPlayedSince)
        ORDER BY r.id, ra.position
        """
    )
    fun observeFilteredJoinRows(
        query: String,
        collectionTypeFilter: CollectionType?,
        statusFilter: ItemStatus?,
        genreFilter: String?,
        formatFilter: AcervusFormat?,
        notPlayedSince: Instant?
    ): Flow<List<RecordWithArtistsRow>>

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

    @Query("UPDATE records SET lastPlayedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun updateLastPlayed(id: String, ts: Instant?)

    @Query("UPDATE records SET status = :status, updatedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: String, status: ItemStatus, ts: Instant)

    @Query("UPDATE records SET rating = :rating, updatedAt = :ts WHERE id = :id")
    suspend fun updateRating(id: String, rating: Int?, ts: Instant)

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
