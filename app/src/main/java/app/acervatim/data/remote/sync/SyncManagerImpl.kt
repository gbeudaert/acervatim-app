package app.acervatim.data.remote.sync

import android.util.Log
import androidx.room.withTransaction
import app.acervatim.data.local.db.AcervatimDatabase
import app.acervatim.data.local.db.ArtistDao
import app.acervatim.data.local.db.ArtistEntity
import app.acervatim.data.local.db.CollectionDao
import app.acervatim.data.local.db.CollectionEntity
import app.acervatim.data.local.db.RecordDao
import app.acervatim.data.local.db.SeriesDao
import app.acervatim.data.local.db.groupToRecords
import app.acervatim.data.local.db.toEntity
import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.CollectionDto
import app.acervatim.data.remote.error.ApiException
import app.acervatim.data.remote.dto.ItemDto
import app.acervatim.data.remote.dto.PatchItemRequest
import app.acervatim.data.remote.mapper.FacadeMapper
import app.acervatim.data.remote.mapper.RemoteDtoMapper
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.normalizeArtistName
import app.acervatim.domain.model.sync.SyncState
import app.acervatim.domain.model.sync.SyncStatus
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.SettingsRepository
import app.acervatim.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moteur de synchronisation push/pull avec résolution **last-write-wins** (§15).
 *
 * Périmètre opérationnel de cette itération :
 * - **Collections** : push (POST/PATCH) + pull, dans les deux sens.
 * - **Items vinyle** (type plat) : push + pull complets — le contrat V1 permet de créer un item plat
 *   sans `node` ni source provider.
 * - **Manga (séries/tomes)** : **non poussé** pour l'instant. Le back exige un `node`/`source` à partir
 *   d'un **provider avec adapter** (MAL) pour créer une série (§7) ; les séries saisies à la main n'ont
 *   pas cette référence. Limite documentée (cf. plan) — à lever quand l'enrichissement MAL sera branché.
 *
 * L'ordre de push respecte les dépendances : **collections → items**. Le pull écrit via les DAO en
 * marquant les lignes [SyncState.SYNCED] (sans repasser par les repos, qui marqueraient DIRTY).
 */
@Singleton
class SyncManagerImpl @Inject constructor(
    private val api: AcervatimApi,
    private val database: AcervatimDatabase,
    private val collectionDao: CollectionDao,
    private val recordDao: RecordDao,
    private val seriesDao: SeriesDao,
    private val artistDao: ArtistDao,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val syncStateStore: SyncStateStore,
    private val clock: Clock
) : SyncRepository {

    private val mutex = Mutex()
    // Réamorce la dernière sync connue depuis le disque : sinon l'UI affiche « Jamais synchronisé »
    // après chaque redémarrage. pushed/pulled à 0 ici, non affichés (SyncStatusLine n'utilise que `.at`).
    private val _status = MutableStateFlow<SyncStatus>(
        syncStateStore.lastSyncAt?.let { SyncStatus.Success(it, pushed = 0, pulled = 0) } ?: SyncStatus.Idle
    )
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    override suspend fun syncNow(): Result<Unit> = mutex.withLock {
        if (authRepository.currentUserId() == null) {
            return Result.failure(IllegalStateException("not signed in"))
        }
        _status.value = SyncStatus.Running
        return runCatching {
            // Un type non coché dans « Gérer les collections » n'est ni poussé ni tiré : ses données
            // restent purement locales. Ensemble vide autorisé (tout garder local).
            val syncedTypes = settingsRepository.observeSyncedCollectionTypes().first()
            val pushResult = push(syncedTypes)
            val pulled = pull(syncedTypes)
            val now = clock.instant()
            _status.value = if (pushResult.skipped > 0) {
                // Quota atteint : sync partielle, pas un échec — surtout pas de retry WorkManager,
                // le quota ne se libérera pas tout seul.
                SyncStatus.QuotaReached(now, pushResult.pushed, pulled, pushResult.skipped)
            } else {
                SyncStatus.Success(now, pushResult.pushed, pulled)
            }
            // Persiste pour réafficher la date après un redémarrage (cf. seed de `_status`).
            syncStateStore.lastSyncAt = now
        }.onFailure { error ->
            Log.w(TAG, "sync failed", error)
            _status.value = SyncStatus.Failure(error.message ?: "erreur inconnue")
        }
    }

    // ---- PUSH -------------------------------------------------------------------------------

    /** Bilan du push : [skipped] = créations refusées pour quota, restées `DIRTY`/`LOCAL_ONLY`. */
    private data class PushResult(val pushed: Int, val skipped: Int)

    private suspend fun push(syncedTypes: Set<CollectionType>): PushResult {
        var count = 0
        var skipped = 0
        // Au premier 402 sur une création, on cesse les POST du même type : le quota ne se libère
        // pas en cours de sync, inutile d'enchaîner les refus (et de risquer un 429). Les PATCH
        // (entités déjà comptées dans le quota) continuent, eux.
        var collectionQuotaHit = false
        var itemQuotaHit = false
        // 1. Collections
        for (col in collectionDao.listNeedingPush()) {
            // Type désactivé pour la sync ⇒ on ne pousse pas cette collection (données locales).
            val colType = CollectionType.fromTypeCode(col.type)
            if (colType != null && colType !in syncedTypes) continue
            val remote = col.toRemoteModel()
            val dto = if (col.remoteId == null) {
                if (collectionQuotaHit) {
                    skipped++
                    continue
                }
                try {
                    api.createCollection(RemoteDtoMapper.run { remote.toCreateRequest() })
                } catch (e: ApiException.QuotaExceeded) {
                    Log.i(TAG, "quota collections atteint — création ${col.id} reportée")
                    collectionQuotaHit = true
                    skipped++
                    continue
                }
            } else {
                api.patchCollection(col.remoteId, RemoteDtoMapper.run { remote.toPatchRequest() })
            }
            collectionDao.updateSync(col.id, dto.id, RemoteDtoMapper.parseInstant(dto.updatedAt), SyncState.SYNCED)
            count++
        }
        // 2. Items vinyle
        for (entity in recordDao.listEntitiesNeedingPush()) {
            if (entity.collectionType != CollectionType.VINYL) {
                Log.i(TAG, "skip push item ${entity.id}: type ${entity.collectionType} non supporté en V1")
                continue
            }
            if (entity.collectionType !in syncedTypes) continue
            val collection = collectionDao.getById(entity.collectionId)
            val collectionRemoteId = collection?.remoteId
            if (collectionRemoteId == null) {
                Log.w(TAG, "skip push item ${entity.id}: collection non synchronisée")
                continue
            }
            val record = recordDao.joinRowsById(entity.id).groupToRecords().firstOrNull() ?: continue
            val facade = FacadeMapper.recordToRemote(record, entity.collectionId, nodeLocalId = null)
            val dto = if (entity.remoteId == null) {
                if (itemQuotaHit) {
                    skipped++
                    continue
                }
                val request = RemoteDtoMapper.run { facade.item.toCreateRequest(nodeRef = null) }
                try {
                    api.createItem(collectionRemoteId, request)
                } catch (e: ApiException.QuotaExceeded) {
                    Log.i(TAG, "quota items atteint — création ${entity.id} reportée")
                    itemQuotaHit = true
                    skipped++
                    continue
                }
            } else {
                api.patchItem(entity.remoteId, PatchItemRequest(facade.item.unifiedData))
            }
            recordDao.updateSync(entity.id, dto.id, RemoteDtoMapper.parseInstant(dto.updatedAt), SyncState.SYNCED)
            count++
        }
        return PushResult(pushed = count, skipped = skipped)
    }

    // ---- PULL -------------------------------------------------------------------------------

    private suspend fun pull(syncedTypes: Set<CollectionType>): Int {
        var count = 0
        // 1. Collections (paginées)
        var cursor: String? = null
        do {
            val page = api.listCollections(cursor = cursor)
            for (dto in page.data) {
                // Type désactivé pour la sync ⇒ on n'importe pas cette collection.
                val type = CollectionType.fromTypeCode(dto.type)
                if (type != null && type !in syncedTypes) continue
                upsertPulledCollection(dto)
                count++
            }
            cursor = page.meta.pagination.nextCursor
        } while (cursor != null)

        // 2. Items par collection synchronisée (vinyle uniquement pour cette itération)
        for (col in collectionDao.listAll()) {
            val remoteId = col.remoteId ?: continue
            val type = CollectionType.fromTypeCode(col.type) ?: continue
            if (type != CollectionType.VINYL) continue
            if (type !in syncedTypes) continue
            var itemCursor: String? = null
            do {
                val page = api.listItems(remoteId, cursor = itemCursor)
                for (light in page.data) {
                    val itemId = light.idOrNull() ?: continue
                    val detail = api.getItem(itemId)
                    if (upsertPulledItem(detail, col, type)) count++
                }
                itemCursor = page.meta.pagination.nextCursor
            } while (itemCursor != null)
        }
        return count
    }

    private suspend fun upsertPulledCollection(dto: CollectionDto) {
        val existing = collectionDao.getByRemoteId(dto.id)
        val serverUpdated = RemoteDtoMapper.parseInstant(dto.updatedAt)
        if (existing == null) {
            collectionDao.upsert(
                CollectionEntity(
                    id = UUID.randomUUID().toString(),
                    remoteId = dto.id,
                    type = dto.type,
                    name = dto.name,
                    description = dto.description,
                    itemCount = dto.itemCount,
                    syncState = SyncState.SYNCED,
                    createdAt = RemoteDtoMapper.parseInstant(dto.createdAt),
                    updatedAt = serverUpdated,
                    serverUpdatedAt = serverUpdated
                )
            )
        } else if (existing.syncState == SyncState.SYNCED) {
            // LWW : pas de modif locale en attente ⇒ on adopte la version serveur.
            collectionDao.upsert(
                existing.copy(
                    type = dto.type,
                    name = dto.name,
                    description = dto.description,
                    itemCount = dto.itemCount,
                    updatedAt = serverUpdated,
                    serverUpdatedAt = serverUpdated
                )
            )
        }
    }

    /** @return true si une ligne a été écrite (serveur gagnant), false si le local l'emporte. */
    private suspend fun upsertPulledItem(dto: ItemDto, col: CollectionEntity, type: CollectionType): Boolean {
        val existing = recordDao.getByRemoteId(dto.id)
        val serverUpdated = RemoteDtoMapper.parseInstant(dto.updatedAt)
        // LWW : si une version locale modifiée est plus récente que le serveur, on la garde (à pousser).
        if (existing != null && existing.syncState != SyncState.SYNCED && existing.updatedAt.isAfter(serverUpdated)) {
            return false
        }
        val localId = existing?.id ?: UUID.randomUUID().toString()
        // Préserve les champs app-only locaux (note, prix, vitesse, dernière écoute…) lors d'un pull.
        val preservedExtras: JsonObject = if (existing != null) {
            val existingRecord = recordDao.joinRowsById(existing.id).groupToRecords().firstOrNull()
            if (existingRecord != null) FacadeMapper.recordToRemote(existingRecord, col.id, null).localExtras
            else JsonObject(emptyMap())
        } else {
            JsonObject(emptyMap())
        }
        val remoteItem = RemoteDtoMapper.run { dto.toRemote(localId = localId, collectionLocalId = col.id, nodeLocalId = null) }
        val record: Record = FacadeMapper.remoteToRecord(
            item = remoteItem,
            type = type,
            seriesId = null,
            localExtras = preservedExtras,
            createdAt = RemoteDtoMapper.parseInstant(dto.createdAt)
        )
        if (record.artists.isEmpty()) {
            // Un item plat sans creators : on évite l'invariant "au moins un artiste" en posant un vide.
            Log.i(TAG, "item ${dto.id} sans creators — ignoré")
            return false
        }
        database.withTransaction {
            recordDao.upsertRecord(
                record.toEntity(
                    collectionId = col.id,
                    remoteId = dto.id,
                    syncState = SyncState.SYNCED,
                    serverUpdatedAt = serverUpdated
                )
            )
            val artistIds = record.artists.map { resolveOrCreateArtist(it.name) }
            recordDao.replaceCrossRefs(record.id, artistIds)
            artistDao.deleteUnused()
        }
        return true
    }

    private suspend fun resolveOrCreateArtist(name: String): String {
        val trimmed = name.trim()
        val normalized = normalizeArtistName(trimmed)
        artistDao.findByNormalizedName(normalized)?.let { return it.id }
        val entity = ArtistEntity(id = UUID.randomUUID().toString(), name = trimmed, normalizedName = normalized)
        artistDao.upsert(entity)
        return entity.id
    }

    private fun CollectionEntity.toRemoteModel() =
        app.acervatim.domain.model.sync.RemoteCollection(
            localId = id,
            remoteId = remoteId,
            type = type,
            name = name,
            description = description,
            itemCount = itemCount,
            syncState = syncState,
            updatedAt = updatedAt,
            serverUpdatedAt = serverUpdatedAt
        )

    private fun JsonObject.idOrNull(): String? = this["id"]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val TAG = "AcervatimSync"
    }
}
