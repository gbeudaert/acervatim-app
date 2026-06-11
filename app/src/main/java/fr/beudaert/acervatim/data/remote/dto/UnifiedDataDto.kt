package fr.beudaert.acervatim.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Profils typés de `unifiedData` (§9 du contrat). Servent à encoder/décoder le contenu curé d'un
 * item ou d'un nœud de façon **stricte** : côté envoi, seuls les champs documentés sont émis (le
 * back valide en `.strict()` → toute clé inconnue ⇒ `400`).
 *
 * Dans les DTO de transport (`ItemDto`, `NodeDto`), `unifiedData` reste un `JsonObject` brut ; ces
 * classes sont (dé)sérialisées à la demande par les mappers, selon le `type` de la collection.
 */

/** §9.1 — item `vinyl` (type plat). Le champ `type` est forcé serveur, on ne l'envoie pas. */
@Serializable
data class VinylUnifiedDataDto(
    val title: String,
    val creators: List<String> = emptyList(),
    val genre: List<String> = emptyList(),
    val label: String? = null,
    val format: String? = null,
    /** Date ISO `YYYY-MM-DD` ou `null`. */
    val releaseDate: String? = null,
    val coverUrl: String? = null
)

/** §9.2 — item/tome `manga`. `volume` n'est PAS ici (champ structurel du corps de requête). */
@Serializable
data class MangaItemUnifiedDataDto(
    val title: String? = null,
    val coverUrl: String? = null
)

/** §9.2 — nœud/série `manga` (pré-rempli via MAL, schéma `.strip()` côté back). */
@Serializable
data class MangaNodeUnifiedDataDto(
    val title: String,
    val author: String? = null,
    @SerialName("status") val status: String = "unknown",
    val totalCount: Int? = null,
    val rating: Double? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null
)
