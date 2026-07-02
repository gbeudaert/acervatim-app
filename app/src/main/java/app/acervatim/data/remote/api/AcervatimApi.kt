package app.acervatim.data.remote.api

import app.acervatim.data.remote.dto.AttachSourceRequest
import app.acervatim.data.remote.dto.CollectionDto
import app.acervatim.data.remote.dto.ConnectedProviderDto
import app.acervatim.data.remote.dto.CreateCollectionRequest
import app.acervatim.data.remote.dto.CreateItemRequest
import app.acervatim.data.remote.dto.CreateNodeRequest
import app.acervatim.data.remote.dto.DeletedResponse
import app.acervatim.data.remote.dto.EditionMappingDto
import app.acervatim.data.remote.dto.ItemDto
import app.acervatim.data.remote.dto.MeResponse
import app.acervatim.data.remote.dto.NodeDto
import app.acervatim.data.remote.dto.OAuthStartResponse
import app.acervatim.data.remote.dto.PageDto
import app.acervatim.data.remote.dto.PatchCollectionRequest
import app.acervatim.data.remote.dto.PatchItemRequest
import app.acervatim.data.remote.dto.PatchNodeRequest
import app.acervatim.data.remote.dto.QuotaResponse
import app.acervatim.data.remote.dto.RedeemInvitationRequest
import app.acervatim.data.remote.dto.RedeemInvitationResponse
import app.acervatim.data.remote.dto.SourceWithRawDto
import app.acervatim.data.remote.dto.SubscriptionDto
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.data.remote.dto.VerifySubscriptionRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API métier du backend Acervatim (toutes routes JWT requis). Le `Authorization: Bearer` est ajouté
 * par [app.acervatim.data.remote.interceptor.AuthInterceptor]. Récapitulatif des endpoints : §14.
 *
 * Les erreurs non-2xx sont converties en [app.acervatim.data.remote.error.ApiException] par
 * l'`ErrorInterceptor` ; les méthodes renvoient donc directement le DTO de succès. Les réponses `204`
 * (sans corps) renvoient `Response<Unit>`.
 */
interface AcervatimApi {

    // ---- /me (§4) ----------------------------------------------------------------------------

    @GET("v1/me")
    suspend fun me(): MeResponse

    @GET("v1/me/quota")
    suspend fun quota(): QuotaResponse

    @DELETE("v1/me")
    suspend fun deleteAccount(): DeletedResponse

    // ---- Collections (§5) --------------------------------------------------------------------

    @POST("v1/collections")
    suspend fun createCollection(@Body body: CreateCollectionRequest): CollectionDto

    @GET("v1/collections")
    suspend fun listCollections(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("type[in]") typeIn: String? = null,
        @Query("items[in]") itemsIn: String? = null,
        @Query("items[all]") itemsAll: String? = null
    ): PageDto<CollectionDto>

    @GET("v1/collections/{id}")
    suspend fun getCollection(@Path("id") id: String): CollectionDto

    @PATCH("v1/collections/{id}")
    suspend fun patchCollection(
        @Path("id") id: String,
        @Body body: PatchCollectionRequest
    ): CollectionDto

    @DELETE("v1/collections/{id}")
    suspend fun deleteCollection(@Path("id") id: String): Response<Unit>

    // ---- Items (§6) --------------------------------------------------------------------------

    @POST("v1/collections/{collectionId}/items")
    suspend fun createItem(
        @Path("collectionId") collectionId: String,
        @Body body: CreateItemRequest
    ): ItemDto

    /** Vue légère (projection dépendant du type, §9.3) — capturée en `JsonObject`. */
    @GET("v1/collections/{collectionId}/items")
    suspend fun listItems(
        @Path("collectionId") collectionId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("provider[in]") providerIn: String? = null,
        @Query("nodeId") nodeId: String? = null
    ): PageDto<JsonObject>

    @GET("v1/items/{id}")
    suspend fun getItem(@Path("id") id: String): ItemDto

    @GET("v1/items/{id}/sources")
    suspend fun getItemSources(@Path("id") id: String): List<SourceWithRawDto>

    @POST("v1/items/{id}/sources")
    suspend fun attachItemSource(
        @Path("id") id: String,
        @Body body: AttachSourceRequest
    ): ItemDto

    @PATCH("v1/items/{id}")
    suspend fun patchItem(@Path("id") id: String, @Body body: PatchItemRequest): ItemDto

    @DELETE("v1/items/{id}")
    suspend fun deleteItem(@Path("id") id: String): Response<Unit>

    // ---- Nodes (§7) --------------------------------------------------------------------------

    @POST("v1/collections/{collectionId}/nodes")
    suspend fun createNode(
        @Path("collectionId") collectionId: String,
        @Body body: CreateNodeRequest
    ): NodeDto

    @GET("v1/collections/{collectionId}/nodes")
    suspend fun listNodes(
        @Path("collectionId") collectionId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("level") level: String? = null
    ): PageDto<NodeDto>

    @GET("v1/nodes/{id}")
    suspend fun getNode(@Path("id") id: String): NodeDto

    @GET("v1/nodes/{id}/sources")
    suspend fun getNodeSources(@Path("id") id: String): List<SourceWithRawDto>

    /** `PATCH` peut purger un nœud vide (`isWishlist:false`) → `204` sans corps, d'où `Response`. */
    @PATCH("v1/nodes/{id}")
    suspend fun patchNode(@Path("id") id: String, @Body body: PatchNodeRequest): Response<NodeDto>

    @POST("v1/nodes/{id}/sources")
    suspend fun attachNodeSource(
        @Path("id") id: String,
        @Body body: AttachSourceRequest
    ): NodeDto

    // ---- Recherche externe (§8) --------------------------------------------------------------

    /**
     * Recherche externe unifiée (§8). `q` (texte libre) et `barcode` (EAN/UPC ou ISBN) sont en **XOR**
     * : exactement l'un des deux doit être fourni. Pour le vinyle, `barcode` route vers Discogs
     * `database/search?barcode=…` côté serveur.
     */
    @GET("v1/search")
    suspend fun search(
        @Query("type") type: String,
        @Query("q") query: String? = null,
        @Query("barcode") barcode: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): PageDto<UnifiedItemDto>

    /**
     * Énumère une édition manga complète (« récupérer toute la série d'un coup ») via la BnF.
     * `title` = titre FR de la série (source : `metadata.scannedTome.titleFr` d'un précédent
     * `/search`) ; `edition` = mention 205 (absent → édition standard).
     */
    @GET("v1/search/edition-mapping")
    suspend fun editionMapping(
        @Query("title") title: String,
        @Query("edition") edition: String? = null
    ): EditionMappingDto

    // ---- OAuth utilisateur (§10) -------------------------------------------------------------

    @GET("v1/oauth/{provider}/start")
    suspend fun startOAuth(@Path("provider") provider: String): OAuthStartResponse

    @GET("v1/oauth")
    suspend fun connectedProviders(): PageDto<ConnectedProviderDto>

    @DELETE("v1/oauth/{provider}")
    suspend fun revokeOAuth(@Path("provider") provider: String): Response<Unit>

    // ---- Premium (§11) -----------------------------------------------------------------------

    @POST("v1/subscriptions/verify")
    suspend fun verifySubscription(@Body body: VerifySubscriptionRequest): SubscriptionDto

    @POST("v1/invitations/redeem")
    suspend fun redeemInvitation(@Body body: RedeemInvitationRequest): RedeemInvitationResponse
}
