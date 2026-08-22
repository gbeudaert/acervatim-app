package app.acervatim.data.remote.sync

import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.AttachSourceRequest
import app.acervatim.data.remote.error.ApiException
import app.acervatim.data.remote.dto.CollectionDto
import app.acervatim.data.remote.dto.ConnectedProviderDto
import app.acervatim.data.remote.dto.CoverResponseDto
import app.acervatim.data.remote.dto.CreateCollectionRequest
import app.acervatim.data.remote.dto.CreateImportJobRequest
import app.acervatim.data.remote.dto.CreateItemRequest
import app.acervatim.data.remote.dto.CreateNodeRequest
import app.acervatim.data.remote.dto.CreateShareRequest
import app.acervatim.data.remote.dto.CreatedShareDto
import app.acervatim.data.remote.dto.DeletedResponse
import app.acervatim.data.remote.dto.EditionMappingDto
import app.acervatim.data.remote.dto.ImportJobDto
import app.acervatim.data.remote.dto.ImportJobStatusDto
import app.acervatim.data.remote.dto.ItemDto
import app.acervatim.data.remote.dto.MeResponse
import app.acervatim.data.remote.dto.NodeDto
import app.acervatim.data.remote.dto.OAuthStartResponse
import app.acervatim.data.remote.dto.PageDto
import app.acervatim.data.remote.dto.PatchCollectionRequest
import app.acervatim.data.remote.dto.PatchItemRequest
import app.acervatim.data.remote.dto.PatchNodeRequest
import app.acervatim.data.remote.dto.RedeemInvitationRequest
import app.acervatim.data.remote.dto.RedeemInvitationResponse
import app.acervatim.data.remote.dto.ShareDto
import app.acervatim.data.remote.dto.SourceWithRawDto
import app.acervatim.data.remote.dto.SubscriptionDto
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.data.remote.dto.VerifySubscriptionRequest
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

    /** `createCollection` lève [ApiException.TechnicalLimit] au-delà de N succès (`null` = jamais). */
    var collectionLimit: Int? = null
    /** `createItem` lève [ApiException.TechnicalLimit] au-delà de N succès (`null` = jamais). */
    var itemLimit: Int? = null
    /** Si non nul, toute route de sync lève ce 402 : la sync est premium-only (S2). */
    var paymentRequired: Boolean = false

    /** Réponses `/v1/me`, redeem — lambdas pour pouvoir lever une [ApiException]. */
    var meHandler: () -> MeResponse = { notImpl() }
    var redeemHandler: (RedeemInvitationRequest) -> RedeemInvitationResponse = { notImpl() }

    var pullCount = 0
        private set

    private val ts = "2026-06-09T10:00:00Z"

    override suspend fun createCollection(body: CreateCollectionRequest): CollectionDto {
        if (paymentRequired) throw ApiException.PaymentRequired(null)
        collectionLimit?.let { limit ->
            if (createdCollections.size >= limit) throw ApiException.TechnicalLimit(null)
        }
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
        if (paymentRequired) throw ApiException.PaymentRequired(null)
        itemLimit?.let { limit ->
            if (createdItems.size >= limit) throw ApiException.TechnicalLimit(null)
        }
        createdItems += body
        return ItemDto(
            id = "item-${UUID.randomUUID()}",
            collectionId = collectionId,
            unifiedData = body.unifiedData,
            userData = body.userData ?: JsonObject(emptyMap()),
            createdAt = ts,
            updatedAt = ts
        )
    }

    override suspend fun patchItem(id: String, body: PatchItemRequest): ItemDto {
        patchedItems += id to body
        return ItemDto(
            id = id,
            collectionId = "c",
            unifiedData = body.unifiedData,
            userData = body.userData ?: JsonObject(emptyMap()),
            createdAt = ts,
            updatedAt = ts
        )
    }

    override suspend fun listCollections(
        cursor: String?, limit: Int?, typeIn: String?, itemsIn: String?, itemsAll: String?
    ): PageDto<CollectionDto> {
        pullCount++
        return PageDto(data = collectionsToReturn)
    }

    override suspend fun listItems(
        collectionId: String, cursor: String?, limit: Int?, providerIn: String?, nodeId: String?
    ): PageDto<JsonObject> {
        val ids = itemIdsByCollection[collectionId].orEmpty()
        val light = ids.map { id -> JsonObject(mapOf("id" to kotlinx.serialization.json.JsonPrimitive(id))) }
        return PageDto(data = light)
    }

    override suspend fun getItem(id: String): ItemDto =
        itemsById[id] ?: error("no item $id")

    // --- compte ------------------------------------------------------------------------------

    override suspend fun me(): MeResponse = meHandler()
    override suspend fun redeemInvitation(body: RedeemInvitationRequest): RedeemInvitationResponse =
        redeemHandler(body)

    // --- routes non utilisées par les tests -------------------------------------------------

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
    override suspend fun search(type: String, query: String?, barcode: String?, cursor: String?, limit: Int?): PageDto<UnifiedItemDto> = notImpl()
    override suspend fun editionMapping(title: String, edition: String?): EditionMappingDto = notImpl()
    override suspend fun cover(isbn: String, title: String?, volume: Int?, edition: String?): CoverResponseDto = notImpl()
    override suspend fun startImportJob(body: CreateImportJobRequest): ImportJobDto = notImpl()
    override suspend fun importJobStatus(id: String): ImportJobStatusDto = notImpl()
    override suspend fun startOAuth(provider: String): OAuthStartResponse = notImpl()
    override suspend fun connectedProviders(): PageDto<ConnectedProviderDto> = notImpl()
    override suspend fun revokeOAuth(provider: String): Response<Unit> = notImpl()
    override suspend fun verifySubscription(body: VerifySubscriptionRequest): SubscriptionDto = notImpl()
    override suspend fun createShare(id: String, body: CreateShareRequest): CreatedShareDto = notImpl()
    override suspend fun listShares(id: String): List<ShareDto> = notImpl()
    override suspend fun revokeShare(id: String): Response<Unit> = notImpl()
    override suspend fun revokeShareMember(id: String, memberUserId: String): Response<Unit> = notImpl()

    private fun notImpl(): Nothing = throw NotImplementedError("not needed in tests")
}
