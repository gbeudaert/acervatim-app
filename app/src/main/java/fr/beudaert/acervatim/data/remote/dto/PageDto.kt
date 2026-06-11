package fr.beudaert.acervatim.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enveloppe de liste paginée du contrat back (§1, « Pagination cursor only ») :
 *
 * ```json
 * { "data": [ … ], "meta": { "pagination": { "nextCursor": "abc", "limit": 50 } } }
 * ```
 *
 * Les ressources *unitaires* (`GET …/:id`) renvoient l'objet brut sans cette enveloppe.
 */
@Serializable
data class PageDto<T>(
    val data: List<T> = emptyList(),
    val meta: PageMetaDto = PageMetaDto()
)

@Serializable
data class PageMetaDto(
    val pagination: PaginationDto = PaginationDto()
)

@Serializable
data class PaginationDto(
    /** `id` du dernier élément de la page (opaque). `null` ⇒ dernière page. */
    @SerialName("nextCursor") val nextCursor: String? = null,
    val limit: Int = 50
)
