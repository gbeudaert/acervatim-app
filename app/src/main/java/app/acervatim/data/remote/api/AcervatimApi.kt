package app.acervatim.data.remote.api

import app.acervatim.data.remote.dto.AttachSourceRequest
import app.acervatim.data.remote.dto.CollectionDto
import app.acervatim.data.remote.dto.ConnectedProviderDto
import app.acervatim.data.remote.dto.CreateCollectionRequest
import app.acervatim.data.remote.dto.CreateItemRequest
import app.acervatim.data.remote.dto.CoverResponseDto
import app.acervatim.data.remote.dto.CreateImportJobRequest
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
import app.acervatim.data.remote.dto.RedeemShareRequest
import app.acervatim.data.remote.dto.RedeemedShareDto
import app.acervatim.data.remote.dto.ReceivedShareDto
import app.acervatim.data.remote.dto.SeriesEditionsDto
import app.acervatim.data.remote.dto.SeriesUpdatesDto
import app.acervatim.data.remote.dto.ShareDto
import app.acervatim.data.remote.dto.UpdateShareLabelRequest
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
     * `/search`, ou `seriesTitleFr` d'une édition de `search/editions`) ; `edition` = mention 205
     * (absent → édition standard) ; `publisher` = éditeur de l'édition (absent → le serveur retient
     * l'éditeur dominant) ; `mangaId` = jointure directe des jaquettes ; `since` (`AAAAMMJJ`) = ne
     * renvoyer que les tomes dont la notice BnF a été créée à cette date ou après (rattrapage).
     */
    @GET("v1/search/edition-mapping")
    suspend fun editionMapping(
        @Query("title") title: String,
        @Query("edition") edition: String? = null,
        @Query("publisher") publisher: String? = null,
        @Query("mangaId") mangaId: String? = null,
        @Query("since") since: String? = null
    ): EditionMappingDto

    /**
     * Éditions françaises (BnF) d'une série MangaDex choisie par l'utilisateur. Le serveur relit
     * lui-même l'identité MangaDex (titres, auteurs) : l'app ne transmet que l'id, plus un titre de
     * repli facultatif.
     */
    @GET("v1/search/editions")
    suspend fun searchEditions(
        @Query("mangaId") mangaId: String,
        @Query("title") title: String? = null
    ): SeriesEditionsDto

    /** Nouveaux tomes des séries synchronisées (job serveur hebdomadaire, premium). */
    @GET("v1/series-updates")
    suspend fun seriesUpdates(): SeriesUpdatesDto

    /**
     * Résout la jaquette d'un tome par `isbn` via Google Books côté serveur : résout (réseau) **et**
     * alimente le cache serveur qui peuple ensuite `edition-mapping`. Appel **explicite** (déclenché
     * par l'import, politique réseau §6) ; renvoie `{ coverUrl: null }` si aucune jaquette.
     *
     * `title` + `volume` (et `edition` si spéciale) arment le **repli par titre** côté serveur
     * (`intitle:<title> T<volume>`) : indispensable pour les ISBN papier FR (Ki-oon…) qui n'ont
     * souvent aucune notice illustrée par ISBN. Sans eux, seule la résolution `isbn:` est tentée.
     */
    @GET("v1/search/cover")
    suspend fun cover(
        @Query("isbn") isbn: String,
        @Query("title") title: String? = null,
        @Query("volume") volume: Int? = null,
        @Query("edition") edition: String? = null
    ): CoverResponseDto

    /**
     * Lance (ou réutilise) l'import **asynchrone** d'une édition complète. `202` + `{ jobId, state }`.
     * Le `jobId` est déterministe (dédup inter-user) : deux demandes identiques partagent le job.
     * L'app poll ensuite [importJobStatus] puis, une fois `done`, re-tape [editionMapping] (cache chaud).
     */
    @POST("v1/search/import-jobs")
    suspend fun startImportJob(@Body body: CreateImportJobRequest): ImportJobDto

    /** Statut + avancement d'un job d'import. `404` (`ApiException.NotFound`) si l'id est inconnu. */
    @GET("v1/search/import-jobs/{id}")
    suspend fun importJobStatus(@Path("id") id: String): ImportJobStatusDto

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

    // ---- Partages de collection (§12) --------------------------------------------------------

    /**
     * Émet un partage. Premium requis sur le **requérant** (402 sinon) : partager suppose la sync
     * cloud. La réponse porte le code en clair — **la seule fois** : le serveur n'en garde que le
     * HMAC. Ne jamais le journaliser ni le persister.
     *
     * Un partage porte N collections avec leurs statuts : il n'appartient donc plus à une collection,
     * d'où l'ancrage sur `/v1/shares` (sprint backend S5).
     */
    @POST("v1/shares")
    suspend fun createShare(@Body body: CreateShareRequest): CreatedShareDto

    /**
     * Partages non révoqués que j'ai émis (liste nue, sans enveloppe de pagination), membres actifs
     * inclus. Les partages **expirés** y figurent encore : le propriétaire doit pouvoir voir pourquoi
     * son code ne prend plus.
     *
     * [collectionId] restreint aux partages exposant cette collection — la collection n'est plus
     * qu'un filtre. Hors gate premium : un premium expiré doit pouvoir constater et retirer.
     */
    @GET("v1/shares")
    suspend fun listShares(@Query("collectionId") collectionId: String? = null): List<ShareDto>

    /**
     * Renomme un partage que j'ai émis. **Seul champ modifiable** : re-scoper un partage vivant
     * changerait sans le dire ce que ses membres voient déjà — cela passe par révocation + nouveau code.
     */
    @PATCH("v1/shares/{id}")
    suspend fun updateShareLabel(
        @Path("id") id: String,
        @Body body: UpdateShareLabelRequest
    ): ShareDto

    /** Révoque le partage entier (code + tous ses membres). `204` sans corps ; idempotent. */
    @DELETE("v1/shares/{id}")
    suspend fun revokeShare(@Path("id") id: String): Response<Unit>

    /** Éjecte un seul membre ; le code reste valide pour les autres. `204` sans corps. */
    @DELETE("v1/shares/{id}/members/{memberUserId}")
    suspend fun revokeShareMember(
        @Path("id") id: String,
        @Path("memberUserId") memberUserId: String
    ): Response<Unit>

    /**
     * Consomme un code de partage. **Hors gate premium** : c'est le propriétaire qui paie le
     * stockage, pas le membre. Idempotent (ré-appeler ne consomme pas de place).
     *
     * Rate-limité serré côté serveur (5/min) — un `429` n'est pas une panne, c'est la défense
     * anti-balayage. Tout refus lié au code sort en `404`, sans dire lequel : pas d'oracle.
     */
    @POST("v1/shares/redeem")
    suspend fun redeemShare(@Body body: RedeemShareRequest): RedeemedShareDto

    /** Partages que j'ai rejoints et qui vivent encore, avec **mon** libellé (jamais celui de l'autre). */
    @GET("v1/shares/received")
    suspend fun receivedShares(): List<ReceivedShareDto>

    /** Renomme, côté membre, un partage rejoint. Le propriétaire ne voit pas ce libellé. */
    @PATCH("v1/shares/received/{shareId}")
    suspend fun updateReceivedShareLabel(
        @Path("shareId") shareId: String,
        @Body body: UpdateShareLabelRequest
    ): ReceivedShareDto
}
