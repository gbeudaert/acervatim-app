package app.acervatim.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.sync.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SeriesDao {

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SeriesEntity?>

    @Query(
        """
        SELECT * FROM series
        WHERE collectionType = :collectionType
        ORDER BY title COLLATE NOCASE
        """
    )
    fun observeByCollectionType(collectionType: CollectionType): Flow<List<SeriesEntity>>

    @Query("SELECT COUNT(*) FROM series WHERE collectionType = :collectionType")
    fun observeCountByCollectionType(collectionType: CollectionType): Flow<Int>

    @Query(
        """
        SELECT s.*,
               COALESCE(SUM(CASE WHEN r.status = 'OWNED' THEN 1 ELSE 0 END), 0) AS ownedVolumes,
               COALESCE(SUM(CASE WHEN r.status = 'WISHLIST' THEN 1 ELSE 0 END), 0) AS wishlistVolumes,
               COALESCE(SUM(CASE WHEN r.status = 'IGNORED' THEN 1 ELSE 0 END), 0) AS ignoredVolumes,
               COALESCE(COUNT(r.id), 0) AS totalVolumesInLibrary,
               MAX(r.lastPlayedAt) AS lastPlayedAt,
               COALESCE(SUM(r.playCount), 0) AS playCount
        FROM series s
        LEFT JOIN records r ON r.seriesId = s.id
        WHERE s.collectionType = :collectionType
        GROUP BY s.id
        ORDER BY s.title COLLATE NOCASE
        """
    )
    fun observeWithCountsByCollectionType(
        collectionType: CollectionType
    ): Flow<List<SeriesWithCountsRow>>

    /**
     * Recherche par clé naturelle (cf. index unique sur
     * `(collectionType, normalizedTitle, normalizedAuthor)`). Le caller doit normaliser ses
     * arguments via `normalizeSeriesKey` avant l'appel.
     */
    @Query(
        """
        SELECT * FROM series
        WHERE collectionType = :collectionType
          AND normalizedTitle = :normalizedTitle
          AND normalizedAuthor = :normalizedAuthor
        LIMIT 1
        """
    )
    suspend fun findByNaturalKey(
        collectionType: CollectionType,
        normalizedTitle: String,
        normalizedAuthor: String
    ): SeriesEntity?

    @Query("SELECT * FROM series ORDER BY title COLLATE NOCASE")
    suspend fun listAll(): List<SeriesEntity>

    @Upsert
    suspend fun upsert(series: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM series")
    suspend fun deleteAll()

    // --- Synchronisation ---------------------------------------------------------------------

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun getEntityById(id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE syncState != 'SYNCED'")
    suspend fun listEntitiesNeedingPush(): List<SeriesEntity>

    @Query(
        "UPDATE series SET remoteId = :remoteId, serverUpdatedAt = :serverUpdatedAt, " +
            "syncState = :syncState WHERE id = :id"
    )
    suspend fun updateSync(id: String, remoteId: String?, serverUpdatedAt: Instant?, syncState: SyncState)
}
