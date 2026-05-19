package fr.beudaert.acervus.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import fr.beudaert.acervus.domain.model.CollectionType
import kotlinx.coroutines.flow.Flow

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
               COALESCE(COUNT(r.id), 0) AS totalVolumesInLibrary
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

    @Upsert
    suspend fun upsert(series: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: String)
}
