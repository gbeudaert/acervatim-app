package app.acervatim.data.remote.mapper

import app.acervatim.data.remote.dto.AttachSourceRequest
import app.acervatim.data.remote.dto.CollectionDto
import app.acervatim.data.remote.dto.CreateCollectionRequest
import app.acervatim.data.remote.dto.CreateItemRequest
import app.acervatim.data.remote.dto.CreateNodeRequest
import app.acervatim.data.remote.dto.ItemDto
import app.acervatim.data.remote.dto.NodeDto
import app.acervatim.data.remote.dto.PatchCollectionRequest
import app.acervatim.data.remote.dto.PatchItemRequest
import app.acervatim.data.remote.dto.nodeRef
import app.acervatim.data.remote.dto.volumeRef
import app.acervatim.data.remote.dto.PatchNodeRequest
import app.acervatim.data.remote.dto.SourceDto
import app.acervatim.data.remote.dto.SourceRefDto
import app.acervatim.domain.model.sync.RemoteCollection
import app.acervatim.domain.model.sync.RemoteItem
import app.acervatim.domain.model.sync.RemoteNode
import app.acervatim.domain.model.sync.RemoteSource
import app.acervatim.domain.model.sync.SyncState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Conversion entre les DTO de transport et les modèles « miroir » du domaine
 * ([RemoteCollection]/[RemoteNode]/[RemoteItem]). Fonctions pures (testables en JVM).
 *
 * Les `localId` ne viennent jamais du back : ils sont fournis par le moteur de sync lors de la
 * réconciliation `remoteId → ligne locale`. Les ressources venant du serveur sont marquées
 * [SyncState.SYNCED].
 */
object RemoteDtoMapper {

    // ---- DTO → domaine (pull) ---------------------------------------------------------------

    fun CollectionDto.toRemote(localId: String): RemoteCollection = RemoteCollection(
        localId = localId,
        remoteId = id,
        type = type,
        name = name,
        description = description,
        itemCount = itemCount,
        syncState = SyncState.SYNCED,
        updatedAt = parseInstant(updatedAt),
        serverUpdatedAt = parseInstant(updatedAt)
    )

    fun ItemDto.toRemote(localId: String, collectionLocalId: String, nodeLocalId: String?): RemoteItem =
        RemoteItem(
            localId = localId,
            remoteId = id,
            collectionLocalId = collectionLocalId,
            nodeLocalId = nodeLocalId,
            volume = volume,
            unifiedData = unifiedData,
            userData = userData,
            sources = sources.map { it.toRemote() },
            syncState = SyncState.SYNCED,
            updatedAt = parseInstant(updatedAt),
            serverUpdatedAt = parseInstant(updatedAt)
        )

    fun NodeDto.toRemote(localId: String, collectionLocalId: String, updatedAt: Instant): RemoteNode =
        RemoteNode(
            localId = localId,
            remoteId = id,
            collectionLocalId = collectionLocalId,
            level = level,
            isWishlist = isWishlist,
            ownedCount = ownedCount,
            note = userData.note,
            comment = userData.comment,
            unifiedData = flattenedUnifiedData(),
            sources = sources.map { it.toRemote() },
            syncState = SyncState.SYNCED,
            updatedAt = updatedAt,
            serverUpdatedAt = updatedAt
        )

    private fun SourceDto.toRemote(): RemoteSource =
        RemoteSource(provider = provider, externalId = externalId, fetchedAt = fetchedAt?.let { parseInstantOrNull(it) })

    /** Reconstruit le `unifiedData` du nœud à partir des champs étalés à plat du contrat (§7). */
    private fun NodeDto.flattenedUnifiedData(): JsonObject = buildJsonObject {
        title?.let { put("title", it) }
        author?.let { put("author", it) }
        status?.let { put("status", it) }
        totalCount?.let { put("totalCount", it) }
        rating?.let { put("rating", it) }
        synopsis?.let { put("synopsis", it) }
        coverUrl?.let { put("coverUrl", it) }
        edition?.let { put(FacadeMapper.KEY_EDITION, it) }
        publisherFr?.let { put(FacadeMapper.KEY_PUBLISHER_FR, it) }
        mangaId?.let { put(FacadeMapper.KEY_MANGA_ID, it) }
    }

    // ---- domaine → DTO de requête (push) ----------------------------------------------------

    fun RemoteCollection.toCreateRequest(): CreateCollectionRequest =
        CreateCollectionRequest(typeCode = type, name = name, description = description)

    fun RemoteCollection.toPatchRequest(): PatchCollectionRequest =
        PatchCollectionRequest(name = name, description = description)

    /**
     * [nodeRemoteId] et [nodeRef] sont **exclusifs** — le serveur refuse les deux ensemble.
     * L'id serveur est le chemin de la synchronisation (la série a déjà été poussée) ; la référence
     * provider celui des imports enrichis, qui ne connaissent pas encore cet id.
     */
    fun RemoteItem.toCreateRequest(
        nodeRef: SourceRefDto? = null,
        nodeRemoteId: String? = null
    ): CreateItemRequest = CreateItemRequest(
        unifiedData = unifiedData,
        node = nodeRef.takeIf { nodeRemoteId == null },
        nodeId = nodeRemoteId,
        volume = volume,
        sources = sources.map { SourceRefDto(it.provider, it.externalId) }.ifEmpty { null },
        userData = userData.takeIf { it.isNotEmpty() }
    )

    /**
     * [nodeRemoteId] et [volume] ne sont envoyés que pour un type **hiérarchique** ([serial]).
     *
     * Sur un type plat, les omettre n'est pas une optimisation mais une nécessité : le serveur
     * refuserait un `nodeId` sur une collection sans hiérarchie, et un `volume: null` explicite
     * n'aurait aucun sens à répéter à chaque PATCH de vinyle.
     */
    fun RemoteItem.toPatchRequest(serial: Boolean = false): PatchItemRequest = PatchItemRequest(
        unifiedData = unifiedData,
        // Merge partiel côté serveur : on n'envoie que les clés que l'app modélise.
        userData = userData.takeIf { it.isNotEmpty() },
        nodeId = if (serial) nodeRef(nodeRemoteId) else null,
        volume = if (serial) volumeRef(volume) else null
    )

    /**
     * Au moins l'un de [source] / `unifiedData` doit être présent (backend 0.8.1).
     *
     * La synchronisation passe par `unifiedData` : une série locale peut n'avoir aucune référence
     * provider — l'utilisateur l'a saisie à la main — et serait sinon impossible à pousser.
     */
    fun RemoteNode.toCreateRequest(source: SourceRefDto? = null): CreateNodeRequest =
        CreateNodeRequest(
            level = level,
            source = source,
            unifiedData = unifiedData.takeIf { it.isNotEmpty() }
            // `isWishlist` volontairement absent — cf. [toPatchRequest].
        )

    /**
     * **`isWishlist` n'est jamais envoyé**, et ce n'est pas un oubli.
     *
     * Le serveur *supprime* le nœud quand il reçoit `isWishlist: false` sur un nœud sans tome
     * (`nodes.service.ts`). Or l'app ne modélise pas ce drapeau : `RemoteNode.isWishlist` vaut
     * `false` par défaut, et le transmettre aurait purgé toute série dont les tomes ne sont pas
     * encore poussés — pour la recréer à la sync suivante, en boucle.
     *
     * Omettre la clé laisse `undefined` côté serveur, qui ne purge alors rien. Le jour où l'app
     * modélisera les séries désirées sans tome, ce champ redeviendra légitime — mais il faudra
     * alors qu'il porte une vraie intention, pas un défaut de structure.
     */
    fun RemoteNode.toPatchRequest(): PatchNodeRequest = PatchNodeRequest(
        unifiedData = unifiedData,
        note = note,
        comment = comment
    )

    fun RemoteSource.toAttachRequest(): AttachSourceRequest =
        AttachSourceRequest(provider = provider, externalId = externalId)

    // ---- parsing ISO-8601 -------------------------------------------------------------------

    /** Parse une date ISO-8601 ; en dernier recours renvoie l'epoch (le back garantit le format). */
    fun parseInstant(iso: String): Instant = parseInstantOrNull(iso) ?: Instant.EPOCH

    fun parseInstantOrNull(iso: String): Instant? = try {
        Instant.parse(iso)
    } catch (_: DateTimeParseException) {
        null
    }
}
