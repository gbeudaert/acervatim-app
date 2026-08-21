package app.acervatim.data.remote.dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

/**
 * Item curé (détail — `GET /v1/items/:id`, §6). `unifiedData` est la vérité curée (forme dépendant
 * du `type` de la collection, §9) : on la transporte en `JsonObject` brut et on la (dé)code via les
 * profils typés ([VinylUnifiedDataDto], [MangaItemUnifiedDataDto]) dans les mappers.
 *
 * `userData` porte les données perso/subjectives (`status`, `rating`, `purchasePrice`,
 * `lastPlayedAt`, `note`) : transporté brut lui aussi, seul `status` est mappé à ce stade.
 */
@Serializable
data class ItemDto(
    val id: String,
    val collectionId: String,
    val nodeId: String? = null,
    val volume: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    val unifiedData: JsonObject = JsonObject(emptyMap()),
    val userData: JsonObject = JsonObject(emptyMap()),
    val sources: List<SourceDto> = emptyList()
)

/**
 * Corps de `POST /v1/collections/:collectionId/items`.
 * - `node` + `volume` : requis pour les types hiérarchiques (manga), interdits pour les types plats.
 * - `unifiedData` : validé par le profil du type, ≤ 32 000 octets sérialisés.
 * - `userData` : omis quand vide (`encodeDefaults = false`), le serveur appliquant alors ses défauts.
 */
@Serializable
data class CreateItemRequest(
    val unifiedData: JsonObject,
    val node: SourceRefDto? = null,
    val volume: Int? = null,
    val sources: List<SourceRefDto>? = null,
    val userData: JsonObject? = null
)

/**
 * Corps de `PATCH /v1/items/:id` (éditer la vérité curée).
 *
 * `userData` est **partiel côté serveur** : seules les clés envoyées sont mises à jour (merge). Ne
 * jamais y recopier l'objet entier — les champs que l'app ne modélise pas encore (`note`…) seraient
 * écrasés. Omis quand `null`, grâce à `explicitNulls = false`.
 */
@Serializable
data class PatchItemRequest(
    val unifiedData: JsonObject,
    val userData: JsonObject? = null
)
