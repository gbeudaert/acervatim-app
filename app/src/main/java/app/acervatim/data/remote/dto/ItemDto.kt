package app.acervatim.data.remote.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 *
 * Deux façons de rattacher un tome à sa série, **mutuellement exclusives** (le serveur refuse les
 * deux ensemble) :
 * - [nodeId] — id **serveur** d'un nœud déjà poussé. C'est le chemin de la **synchronisation** :
 *   l'app pousse ses séries, puis ses tomes, comme elle fait collections → items.
 * - [node] — référence provider, le serveur upserte le nœud. Chemin des **imports enrichis** (MAL),
 *   qui ne connaissent pas encore l'id serveur de la série.
 *
 * `volume` : `null` = tome hors numérotation (hors-série, artbook). Le modèle app l'autorise.
 * `unifiedData` : validé par le profil du type, ≤ 32 000 octets sérialisés.
 * `userData` : omis quand vide (`encodeDefaults = false`), le serveur appliquant alors ses défauts.
 */
@Serializable
data class CreateItemRequest(
    val unifiedData: JsonObject,
    val node: SourceRefDto? = null,
    val nodeId: String? = null,
    val volume: Int? = null,
    val sources: List<SourceRefDto>? = null,
    val userData: JsonObject? = null
)

/**
 * Corps de `PATCH /v1/items/:id`.
 *
 * `userData` est **partiel côté serveur** : seules les clés envoyées sont mises à jour (merge). Ne
 * jamais y recopier l'objet entier — les champs que l'app ne modélise pas encore (`note`…) seraient
 * écrasés. Omis quand `null`, grâce à `explicitNulls = false`.
 *
 * [nodeId] et [volume] portent l'**identité structurelle** d'un tome (sa série, son numéro), que le
 * backend 0.8.1 a ouverte au PATCH : sans elles, déplacer un tome ou changer son numéro restait
 * local et le miroir serveur gardait indéfiniment l'ancienne valeur.
 *
 * Elles sont typées [JsonElement] et non `String?`/`Int?` **à dessein**. Le `Json` de l'app est en
 * `explicitNulls = false` : un `null` Kotlin ne serait pas sérialisé, donc « détacher un tome de sa
 * série » serait indistinguable de « ne pas toucher à sa série ». `JsonNull`, lui, s'écrit bien
 * `null` dans le corps. Passer par [nodeRef]/[volumeRef] plutôt que de les construire à la main.
 */
@Serializable
data class PatchItemRequest(
    val unifiedData: JsonObject,
    val userData: JsonObject? = null,
    val nodeId: JsonElement? = null,
    val volume: JsonElement? = null
)

/** `null` explicite (détacher) vs absent (ne pas toucher) — cf. [PatchItemRequest]. */
fun nodeRef(id: String?): JsonElement = id?.let(::JsonPrimitive) ?: JsonNull

/** Idem pour un numéro de tome : `JsonNull` = hors numérotation, pas « inchangé ». */
fun volumeRef(volume: Int?): JsonElement = volume?.let(::JsonPrimitive) ?: JsonNull
