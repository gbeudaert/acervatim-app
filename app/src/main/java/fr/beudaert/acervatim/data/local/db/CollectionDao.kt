package fr.beudaert.acervatim.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import fr.beudaert.acervatim.domain.model.sync.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections")
    suspend fun listAll(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CollectionEntity?

    @Query("SELECT * FROM collections WHERE type = :type ORDER BY createdAt LIMIT 1")
    suspend fun getFirstByType(type: String): CollectionEntity?

    @Query("SELECT * FROM collections WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CollectionEntity?

    /** Lignes à pousser (créées ou modifiées localement). */
    @Query("SELECT * FROM collections WHERE syncState != 'SYNCED'")
    suspend fun listNeedingPush(): List<CollectionEntity>

    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Query(
        "UPDATE collections SET remoteId = :remoteId, serverUpdatedAt = :serverUpdatedAt, " +
            "syncState = :syncState WHERE id = :id"
    )
    suspend fun updateSync(id: String, remoteId: String?, serverUpdatedAt: Instant?, syncState: SyncState)
}
