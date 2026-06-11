package fr.beudaert.acervatim.data.remote.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

/**
 * Résultat de recherche externe unifiée (`GET /v1/search`, §8). Le couple `{ source, sourceId }`
 * sert ensuite de `{ provider, externalId }` pour créer un item/nœud.
 *
 * Le curseur de `/search` est **opaque non-UUID** (numéro/URL de page provider).
 */
@Serializable
data class UnifiedItemDto(
    val source: String,
    val sourceId: String,
    val mediaType: String,
    val title: String,
    val creators: List<String> = emptyList(),
    val releaseDate: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val metadata: JsonObject? = null,
    val rawData: JsonElement? = null
)
