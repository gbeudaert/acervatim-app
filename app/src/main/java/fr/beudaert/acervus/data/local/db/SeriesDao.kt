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

    @Upsert
    suspend fun upsert(series: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: String)
}
