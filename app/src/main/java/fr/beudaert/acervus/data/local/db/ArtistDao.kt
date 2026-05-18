package fr.beudaert.acervus.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT name FROM artists ORDER BY name COLLATE NOCASE")
    fun observeAllNames(): Flow<List<String>>

    @Query("SELECT * FROM artists WHERE normalized_name = :normalized LIMIT 1")
    suspend fun findByNormalizedName(normalized: String): ArtistEntity?

    @Upsert
    suspend fun upsert(artist: ArtistEntity)

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT artistId FROM record_artists)")
    suspend fun deleteUnused()
}
