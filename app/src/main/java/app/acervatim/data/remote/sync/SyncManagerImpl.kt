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
import app.acervatim.data.local.db.toDomain
import app.acervatim.data.local.db.toEntity
import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.CollectionDto
import app.acervatim.data.remote.error.ApiException
import app.acervatim.data.remote.dto.ItemDto
import app.acervatim.data.remote.dto.NodeDto
import app.acervatim.data.remote.mapper.FacadeMapper
import app.acervatim.data.remote.mapper.RemoteDtoMapper
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.normalizeArtistName
import app.acervatim.domain.model.normalizeEditionKey
import app.acervatim.domain.model.normalizePublisherKey
import app.acervatim.domain.model.sync.RemoteNode
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
                // Plafond technique atteint : sync partielle, pas un échec — surtout pas de retry
                // WorkManager, le plafond ne se libérera pas tout seul.
                SyncStatus.LimitReached(now, pushResult.pushed, pulled, pushResult.skipped)
            } else {
                SyncStatus.Success(now, pushResult.pushed, pulled)
            }
            // Persiste pour réafficher la date après un redémarrage (cf. seed de `_status`).
            syncStateStore.lastSyncAt = now
        }.onFailure { error ->
            if (error is ApiException.PaymentRequired) {
                // La sync est premium-only (S2) : état terminal, pas une erreur passagère. On ne
                // retente pas — et surtout on ne touche pas aux données locales, qui restent
                // pleinement utilisables hors-ligne.
                Log.i(TAG, "sync refusée : palier premium requis")
                _status.value = SyncStatus.PremiumRequired(clock.instant())
            } else {
                Log.w(TAG, "sync failed", error)
                _status.value = SyncStatus.Failure(error.message ?: "erreur inconnue")
            }
        }
    }

    // ---- PUSH -------------------------------------------------------------------------------

    /** Bilan du push : [skipped] = créations refusées par le plafond technique, restées `DIRTY`. */
    private data class PushResult(val pushed: Int, val skipped: Int)

    private suspend fun push(syncedTypes: Set<CollectionType>): PushResult {
        var count = 0
        var skipped = 0
        // Au premier 409 `technical-limit` sur une création, on cesse les POST du même type : le
        // plafond ne se libère pas en cours de sync, inutile d'enchaîner les refus (et de risquer
        // un 429). Les PATCH (entités déjà comptées) continuent, eux.
        var collectionLimitHit = false
        var itemLimitHit = false
        // 1. Collections
        for (col in collectionDao.listNeedingPush()) {
            // Type désactivé pour la sync ⇒ on ne pousse pas cette collection (données locales).
            val colType = CollectionType.fromTypeCode(col.type)
            if (colType != null && colType !in syncedTypes) continue
            val remote = col.toRemoteModel()
            val dto = if (col.remoteId == null) {
                if (collectionLimitHit) {
                    skipped++
                    continue
                }
                try {
                    api.createCollection(RemoteDtoMapper.run { remote.toCreateRequest() })
                } catch (e: ApiException.TechnicalLimit) {
                    Log.i(TAG, "plafond technique collections — création ${col.id} reportée")
                    collectionLimitHit = true
                    skipped++
                    continue
                }
            } else {
                api.patchCollection(col.remoteId, RemoteDtoMapper.run { remote.toPatchRequest() })
            }
            collectionDao.updateSync(col.id, dto.id, RemoteDtoMapper.parseInstant(dto.updatedAt), SyncState.SYNCED)
            count++
        }
        // 2. Séries (nœuds) — AVANT les items : un tome se rattache à sa série par son id
        // serveur, qui n'existe qu'une fois la série poussée.
        for (entity in seriesDao.listEntitiesNeedingPush()) {
            if (entity.collectionType !in syncedTypes) continue
            val collectionRemoteId = collectionDao.getById(entity.collectionId)?.remoteId
            if (collectionRemoteId == null) {
                Log.w(TAG, "skip push série ${entity.id}: collection non synchronisée")
                continue
            }
            val facade = FacadeMapper.seriesToRemote(entity.toDomain(), entity.collectionId)
            val remoteId = if (entity.remoteId == null) {
                if (collectionLimitHit) {
                    skipped++
                    continue
                }
                try {
                    // Sans référence provider : une série saisie à la main n'en a aucune, et le
                    // backend 0.8.1 accepte désormais `unifiedData` seul pour ce cas.
                    api.createNode(
                        collectionRemoteId,
                        RemoteDtoMapper.run { facade.node.toCreateRequest() }
                    ).id
                } catch (e: ApiException.TechnicalLimit) {
                    Log.i(TAG, "plafond technique — création série ${entity.id} reportée")
                    collectionLimitHit = true
                    skipped++
                    continue
                }
            } else {
                // `PATCH /nodes/:id` peut purger un nœud vide (204 sans corps) : dans ce cas le
                // nœud n'existe plus côté serveur et la série redevient locale.
                val response = api.patchNode(
                    entity.remoteId,
                    RemoteDtoMapper.run { facade.node.toPatchRequest() }
                )
                response.body()?.id
            }
            if (remoteId == null) {
                Log.i(TAG, "série ${entity.id} purgée côté serveur — repassée en local")
                seriesDao.updateSync(entity.id, null, null, SyncState.LOCAL_ONLY)
                continue
            }
            seriesDao.updateSync(entity.id, remoteId, null, SyncState.SYNCED)
            count++
        }
        // 3. Items — tous types synchronisés depuis le backend 0.8.1 (le manga inclus).
        for (entity in recordDao.listEntitiesNeedingPush()) {
            if (entity.collectionType !in syncedTypes) continue
            val collection = collectionDao.getById(entity.collectionId)
            val collectionRemoteId = collection?.remoteId
            if (collectionRemoteId == null) {
                Log.w(TAG, "skip push item ${entity.id}: collection non synchronisée")
                continue
            }
            val record = recordDao.joinRowsById(entity.id).groupToRecords().firstOrNull() ?: continue
            // Un tome dont la série n'est pas encore poussée est reporté : le pousser sans `nodeId`
            // le rattacherait à rien, et le lien serait perdu sans qu'on s'en aperçoive.
            val nodeRemoteId = record.seriesId?.let { seriesDao.getEntityById(it)?.remoteId }
            if (record.seriesId != null && nodeRemoteId == null) {
                Log.w(TAG, "skip push item ${entity.id}: série non synchronisée")
                skipped++
                continue
            }
            val facade = FacadeMapper.recordToRemote(record, entity.collectionId, record.seriesId)
            val item = facade.item.copy(nodeRemoteId = nodeRemoteId)
            val serial = entity.collectionType.supportsSerial
            val dto = if (entity.remoteId == null) {
                if (itemLimitHit) {
                    skipped++
                    continue
                }
                val request = RemoteDtoMapper.run { item.toCreateRequest(nodeRemoteId = nodeRemoteId) }
                try {
                    api.createItem(collectionRemoteId, request)
                } catch (e: ApiException.TechnicalLimit) {
                    Log.i(TAG, "plafond technique items — création ${entity.id} reportée")
                    itemLimitHit = true
                    skipped++
                    continue
                }
            } else {
                api.patchItem(
                    entity.remoteId,
                    RemoteDtoMapper.run { item.toPatchRequest(serial = serial) }
                )
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

        // 2. Séries puis items, par collection synchronisée. L'ordre compte au pull comme au
        // push : un tome tiré avant sa série n'aurait pas de série locale où se rattacher.
        for (col in collectionDao.listAll()) {
            val remoteId = col.remoteId ?: continue
            val type = CollectionType.fromTypeCode(col.type) ?: continue
            if (type !in syncedTypes) continue
            if (type.supportsSerial) {
                var nodeCursor: String? = null
                do {
                    val page = api.listNodes(remoteId, cursor = nodeCursor)
                    for (node in page.data) {
                        if (upsertPulledNode(node, col, type)) count++
                    }
                    nodeCursor = page.meta.pagination.nextCursor
                } while (nodeCursor != null)
            }
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

    /**
     * Écrit une série tirée du serveur.
     *
     * Dédup par **clé naturelle** (type + titre + auteur normalisés) en plus du `remoteId`, comme
     * les items le font par code-barres/ISBN : une série créée hors-ligne, ou restaurée depuis un
     * autre appareil, existe déjà localement sous un autre id. Sans ce repli, l'upsert buterait sur
     * l'index unique `index_series_natural_key`.
     *
     * @return true si une ligne a été écrite.
     */
    private suspend fun upsertPulledNode(
        dto: NodeDto,
        col: CollectionEntity,
        type: CollectionType
    ): Boolean {
        val remoteNode = RemoteNode(
            localId = "",
            remoteId = dto.id,
            collectionLocalId = col.id,
            level = dto.level,
            isWishlist = dto.isWishlist,
            unifiedData = buildJsonObject {
                dto.title?.let { put("title", it) }
                dto.author?.let { put("author", it) }
                dto.status?.let { put("status", it) }
                dto.totalCount?.let { put("totalCount", it) }
                dto.synopsis?.let { put("synopsis", it) }
                dto.coverUrl?.let { put("coverUrl", it) }
                dto.edition?.let { put(FacadeMapper.KEY_EDITION, it) }
                dto.publisherFr?.let { put(FacadeMapper.KEY_PUBLISHER_FR, it) }
                dto.mangaId?.let { put(FacadeMapper.KEY_MANGA_ID, it) }
            },
            updatedAt = clock.instant()
        )
        val title = dto.title ?: return false
        // Clé naturelle v8 : l'édition en fait partie. Deux nœuds « Frieren » (standard, colossale)
        // se retrouvent chacun leur série, au lieu de se fondre dans la première venue.
        val existing = seriesDao.getByRemoteId(dto.id)
            ?: seriesDao.findByNaturalKey(
                collectionType = type,
                normalizedTitle = normalizeArtistName(title),
                normalizedAuthor = normalizeArtistName(dto.author.orEmpty()),
                normalizedPublisher = normalizePublisherKey(dto.publisherFr),
                normalizedEdition = normalizeEditionKey(dto.edition)
            )
        // LWW : une modification locale non poussée l'emporte sur la version serveur.
        if (existing != null && existing.syncState != SyncState.SYNCED) return false

        val localId = existing?.id ?: UUID.randomUUID().toString()
        val localExtras = buildJsonObject { existing?.coverPath?.let { put("coverPath", it) } }
        val series = FacadeMapper.remoteToSeries(
            node = remoteNode.copy(localId = localId),
            type = type,
            localExtras = localExtras,
            createdAt = existing?.createdAt ?: clock.instant()
        )
        seriesDao.upsert(
            series.toEntity(
                collectionId = col.id,
                remoteId = dto.id,
                syncState = SyncState.SYNCED,
                serverUpdatedAt = null
            )
        )
        return true
    }

    /** @return true si une ligne a été écrite (serveur gagnant), false si le local l'emporte. */
    private suspend fun upsertPulledItem(dto: ItemDto, col: CollectionEntity, type: CollectionType): Boolean {
        // Un item jamais synchronisé (créé hors-ligne avant la 1re sync, ou restauré depuis un
        // autre appareil) peut déjà exister en local sous un autre id, sans remoteId : on le
        // retrouve par sa clé naturelle (barcode/isbn, uniques en base) pour éviter un conflit
        // d'index qui ferait échouer silencieusement l'upsert (cf. sync failed / FOREIGN KEY).
        val existing = recordDao.getByRemoteId(dto.id)
            ?: dto.sourceExternalId(FacadeMapper.PROVIDER_BARCODE)?.let { recordDao.getEntityByBarcode(it) }
            ?: dto.sourceExternalId(FacadeMapper.PROVIDER_ISBN)?.let { recordDao.getEntityByIsbn(it) }
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
        // Le tome se rattache à la série locale miroir du nœud serveur. `null` si la série n'a pas
        // (encore) été tirée : le tome existe alors sans série, et la prochaine sync le raccrochera.
        val parent = dto.nodeId?.let { seriesDao.getByRemoteId(it) }
        val remoteItem = RemoteDtoMapper.run {
            dto.toRemote(localId = localId, collectionLocalId = col.id, nodeLocalId = parent?.id)
        }
        var record: Record = FacadeMapper.remoteToRecord(
            item = remoteItem,
            type = type,
            seriesId = parent?.id,
            localExtras = preservedExtras,
            createdAt = RemoteDtoMapper.parseInstant(dto.createdAt)
        )
        if (record.artists.isEmpty()) {
            // `MangaItemSchema` est strict et ne porte aucun `creators` : pour un tome, l'auteur est
            // celui de la **série**, exactement comme le fait la saisie locale. Sans ce repli, tout
            // tome tiré du serveur tomberait sur l'invariant « au moins un artiste ».
            val author = parent?.originalAuthor?.takeIf { it.isNotBlank() }
            record = if (type.supportsSerial) {
                record.copy(artists = listOf(Artist(id = "", name = author ?: UNKNOWN_AUTHOR)))
            } else {
                Log.i(TAG, "item ${dto.id} sans creators — ignoré")
                return false
            }
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

    private fun ItemDto.sourceExternalId(provider: String): String? =
        sources.firstOrNull { it.provider == provider }?.externalId

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

        /** Même repli que la saisie manuelle d'un tome (`AddEditMangaViewModel`). */
        const val UNKNOWN_AUTHOR = "Inconnu"
    }
}
