package fr.beudaert.acervatim.data.remote.sync

import fr.beudaert.acervatim.data.remote.api.AcervatimApi
import fr.beudaert.acervatim.data.remote.dto.AttachSourceRequest
import fr.beudaert.acervatim.data.remote.dto.CollectionDto
import fr.beudaert.acervatim.data.remote.dto.ConnectedProviderDto
import fr.beudaert.acervatim.data.remote.dto.CreateCollectionRequest
import fr.beudaert.acervatim.data.remote.dto.CreateItemRequest
import fr.beudaert.acervatim.data.remote.dto.CreateNodeRequest
import fr.beudaert.acervatim.data.remote.dto.DeletedResponse
import fr.beudaert.acervatim.data.remote.dto.ItemDto
import fr.beudaert.acervatim.data.remote.dto.MeResponse
import fr.beudaert.acervatim.data.remote.dto.NodeDto
import fr.beudaert.acervatim.data.remote.dto.OAuthStartResponse
import fr.beudaert.acervatim.data.remote.dto.PageDto
import fr.beudaert.acervatim.data.remote.dto.PatchCollectionRequest
import fr.beudaert.acervatim.data.remote.dto.PatchItemRequest
import fr.beudaert.acervatim.data.remote.dto.PatchNodeRequest
import fr.beudaert.acervatim.data.remote.dto.QuotaResponse
import fr.beudaert.acervatim.data.remote.dto.RedeemInvitationRequest
import fr.beudaert.acervatim.data.remote.dto.RedeemInvitationResponse
import fr.beudaert.acervatim.data.remote.dto.SourceWithRawDto
import fr.beudaert.acervatim.data.remote.dto.SubscriptionDto
import fr.beudaert.acervatim.data.remote.dto.UnifiedItemDto
import fr.beudaert.acervatim.data.remote.dto.VerifySubscriptionRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import java.util.UUID

/**
 * Faux [AcervatimApi] en mémoire pour les tests de synchronisation. Implémente les routes utiles au
 * push/pull vinyle ; les autres lèvent [NotImplementedError]. Mémorise les corps reçus.
 */
class FakeAcervatimApi : AcervatimApi {

    val createdCollections = mutableListOf<CreateCollectionRequest>()
    val createdItems = mutableListOf<CreateItemRequest>()
    val patchedItems = mutableListOf<Pair<String, PatchItemRequest>>()

    /** Collections renvoyées par `listCollections` (pull). */
    var collectionsToReturn: List<CollectionDto> = emptyList()
    /** Items renvoyés par `getItem` (clé = id). */
    var itemsById: Map<String, ItemDto> = emptyMap()
    /** Ids légers renvoyés par `listItems` (clé = collectionRemoteId). */
    var itemIdsByCollection: Map<String, List<String>> = emptyMap()

    private val ts = "2026-06-09T10:00:00Z"

    override suspend fun createCollection(body: CreateCollectionRequest): CollectionDto {
        createdCollections += body
        return CollectionDto(
            id = "remote-${UUID.randomUUID()}",
            userId = "u1",
            type = body.typeCode,
            name = body.name,
            description = body.description,
            itemCount = 0,
            createdAt = ts,
            updatedAt = ts
        )
    }

    override suspend fun patchCollection(id: String, body: PatchCollectionRequest): CollectionDto =
        CollectionDto(id, "u1", "vinyl", body.name ?: "n", body.description, 0, ts, ts)

    override suspend fun createItem(collectionId: String, body: CreateItemRequest): ItemDto {
        createdItems += body
        return ItemDto(
            id = "item-${UUID.randomUUID()}",
            collectionId = collectionId,
            unifiedData = body.unifiedData,
            createdAt = ts,
            updatedAt = ts
        )
    }

    override suspend fun patchItem(id: String, body: PatchItemRequest): ItemDto {
        patchedItems += id to body
        return ItemDto(id = id, collectionId = "c", unifiedData = body.unifiedData, createdAt = ts, updatedAt = ts)
    }

    override suspend fun listCollections(
        cursor: String?, limit: Int?, typeIn: String?, itemsIn: String?, itemsAll: String?
    ): PageDto<CollectionDto> = PageDto(data = collectionsToReturn)

    override suspend fun listItems(
        collectionId: String, cursor: String?, limit: Int?, providerIn: String?, nodeId: String?
    ): PageDto<JsonObject> {
        val ids = itemIdsByCollection[collectionId].orEmpty()
        val light = ids.map { id -> JsonObject(mapOf("id" to kotlinx.serialization.json.JsonPrimitive(id))) }
        return PageDto(data = light)
    }

    override suspend fun getItem(id: String): ItemDto =
        itemsById[id] ?: error("no item $id")

    // --- routes non utilisées par les tests -------------------------------------------------

    override suspend fun me(): MeResponse = notImpl()
    override suspend fun quota(): QuotaResponse = notImpl()
    override suspend fun deleteAccount(): DeletedResponse = notImpl()
    override suspend fun getCollection(id: String): CollectionDto = notImpl()
    override suspend fun deleteCollection(id: String): Response<Unit> = notImpl()
    override suspend fun getItemSources(id: String): List<SourceWithRawDto> = notImpl()
    override suspend fun attachItemSource(id: String, body: AttachSourceRequest): ItemDto = notImpl()
    override suspend fun deleteItem(id: String): Response<Unit> = notImpl()
    override suspend fun createNode(collectionId: String, body: CreateNodeRequest): NodeDto = notImpl()
    override suspend fun listNodes(collectionId: String, cursor: String?, limit: Int?, level: String?): PageDto<NodeDto> = notImpl()
    override suspend fun getNode(id: String): NodeDto = notImpl()
    override suspend fun getNodeSources(id: String): List<SourceWithRawDto> = notImpl()
    override suspend fun patchNode(id: String, body: PatchNodeRequest): Response<NodeDto> = notImpl()
    override suspend fun attachNodeSource(id: String, body: AttachSourceRequest): NodeDto = notImpl()
    override suspend fun search(query: String, type: String, cursor: String?, limit: Int?): PageDto<UnifiedItemDto> = notImpl()
    override suspend fun startOAuth(provider: String): OAuthStartResponse = notImpl()
    override suspend fun connectedProviders(): PageDto<ConnectedProviderDto> = notImpl()
    override suspend fun revokeOAuth(provider: String): Response<Unit> = notImpl()
    override suspend fun verifySubscription(body: VerifySubscriptionRequest): SubscriptionDto = notImpl()
    override suspend fun redeemInvitation(body: RedeemInvitationRequest): RedeemInvitationResponse = notImpl()

    private fun notImpl(): Nothing = throw NotImplementedError("not needed in tests")
}
