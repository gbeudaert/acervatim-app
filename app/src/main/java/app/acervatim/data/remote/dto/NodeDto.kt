package app.acervatim.data.remote.dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

/**
 * Nœud (série / set) d'un type hiérarchique (§7). Particularité du contrat : les champs de
 * `unifiedData` sont **étalés à la racine** de l'objet (pas sous une clé `unifiedData`). On modélise
 * donc les champs curés du profil série manga directement ici (seul niveau `serie` en V1) ;
 * `ignoreUnknownKeys` absorbe d'éventuels champs d'autres niveaux à venir.
 */
@Serializable
data class NodeDto(
    val id: String,
    val level: String,
    val ownedCount: Int = 0,
    val isWishlist: Boolean = false,
    val userData: NodeUserDataDto = NodeUserDataDto(),
    val sources: List<SourceDto> = emptyList(),
    // Champs de unifiedData (profil série manga), étalés à plat :
    val title: String? = null,
    val author: String? = null,
    val status: String? = null,
    val totalCount: Int? = null,
    val rating: Double? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null,
    // Édition (SD2) : sans elle, deux éditions d'une même série seraient indiscernables sur un
    // second appareil. Absents d'un serveur antérieur, d'où les `null` par défaut.
    val edition: String? = null,
    val publisherFr: String? = null,
    val mangaId: String? = null
)

@Serializable
data class NodeUserDataDto(
    val note: Int? = null,
    val comment: String? = null
)

/**
 * Corps de `POST /v1/collections/:collectionId/nodes`.
 *
 * Deux façons de remplir le nœud, combinables, dont **au moins une** est requise (backend 0.8.1) :
 * - [source] — référence provider ; si un adapter existe (MAL), son snapshot pré-remplit la vérité
 *   curée. Idempotent côté serveur.
 * - [unifiedData] — vérité curée fournie par le client. C'est le chemin de la **saisie manuelle** :
 *   une série créée par l'utilisateur n'a aucune référence provider et, sans ce champ, ne serait
 *   tout simplement pas synchronisable.
 */
@Serializable
data class CreateNodeRequest(
    val level: String,
    val source: SourceRefDto? = null,
    val unifiedData: JsonObject? = null,
    val isWishlist: Boolean? = null
)

/**
 * Corps de `PATCH /v1/nodes/:id` (au moins un champ). `unifiedData` re-validé par le schéma du
 * niveau. `note`/`comment`/`isWishlist` pilotent `userData` et l'état wishlist.
 */
@Serializable
data class PatchNodeRequest(
    val unifiedData: JsonObject? = null,
    val note: Int? = null,
    val comment: String? = null,
    val isWishlist: Boolean? = null
)
