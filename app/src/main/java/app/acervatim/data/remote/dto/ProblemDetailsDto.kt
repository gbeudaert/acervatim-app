package app.acervatim.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Corps d'erreur RFC 9457 (`application/problem+json`) renvoyé par le back (§2).
 *
 * ```json
 * {
 *   "type": "https://api.acervatim/probs/validation-error",
 *   "title": "Invalid request payload",
 *   "status": 400,
 *   "detail": "name: String must contain at most 255 characters",
 *   "instance": "/v1/collections",
 *   "requestId": "f1c0e8b2-…",
 *   "errors": [ { "field": "name", "code": "too_big", "message": "…" } ]
 * }
 * ```
 *
 * `errors[]` n'est présent que pour les erreurs de validation Zod (`validation-error`).
 */
@Serializable
data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val requestId: String? = null,
    val errors: List<ProblemFieldErrorDto>? = null,
    /**
     * Extension RFC 9457 propre à `source-token-required` : la source externe qui réclame un jeton
     * (`"discogs"`, …). C'est elle qui décide vers quel parcours router l'utilisateur.
     */
    val provider: String? = null
) {
    /**
     * Suffixe après `…/probs/` (ex. `validation-error`, `quota-exceeded`), seul élément stable
     * du `type`. `null` si le `type` ne suit pas la convention.
     */
    val typeSlug: String? get() = type?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

@Serializable
data class ProblemFieldErrorDto(
    val field: String? = null,
    val code: String? = null,
    val message: String? = null
)
