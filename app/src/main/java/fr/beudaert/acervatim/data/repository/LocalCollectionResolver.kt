package fr.beudaert.acervatim.data.repository

import fr.beudaert.acervatim.data.local.db.CollectionDao
import fr.beudaert.acervatim.data.local.db.CollectionEntity
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.sync.SyncState
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résout (ou crée à la volée) la collection locale conteneuse d'un [CollectionType]. L'app gère pour
 * l'instant une collection par type ; chaque item/nœud écrit y est rattaché via `collectionId` pour
 * permettre la synchronisation. La collection créée localement part en [SyncState.LOCAL_ONLY] et
 * sera poussée (`POST /v1/collections`) au prochain `sync()`.
 */
@Singleton
class LocalCollectionResolver @Inject constructor(
    private val collectionDao: CollectionDao,
    private val clock: Clock
) {
    suspend fun collectionIdFor(type: CollectionType): String {
        collectionDao.getFirstByType(type.typeCode)?.let { return it.id }
        val now = clock.instant()
        val entity = CollectionEntity(
            id = UUID.randomUUID().toString(),
            remoteId = null,
            type = type.typeCode,
            name = defaultName(type),
            description = null,
            itemCount = 0,
            syncState = SyncState.LOCAL_ONLY,
            createdAt = now,
            updatedAt = now,
            serverUpdatedAt = null
        )
        collectionDao.upsert(entity)
        return entity.id
    }

    private fun defaultName(type: CollectionType): String = when (type) {
        CollectionType.VINYL -> "Mes vinyles"
        CollectionType.MANGA -> "Mes mangas"
    }
}
