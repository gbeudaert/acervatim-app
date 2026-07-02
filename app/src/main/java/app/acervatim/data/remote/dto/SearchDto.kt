package app.acervatim.data.remote.dto

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

/**
 * Énumération d'une édition manga (`GET /v1/search/edition-mapping`, source BnF) : « récupérer toute
 * la série d'un coup ». Cf. `bnf.types.ts#EditionMapping` côté backend.
 */
@Serializable
data class EditionMappingDto(
    val titleFr: String,
    val edition: String? = null,
    val tomeCount: Int,
    val tomes: List<EditionTomeDto> = emptyList(),
    val recordsScanned: Int = 0,
    val ongoing: Boolean = false
)

@Serializable
data class EditionTomeDto(
    val editionVolume: Int,
    val sourceVolumeRange: String? = null,
    val isbn: String? = null,
    val titleFr: String? = null
)
