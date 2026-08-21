package app.acervatim.data.remote.error

import app.acervatim.data.remote.dto.ProblemDetailsDto
import java.io.IOException

/**
 * Exception métier mappée depuis une réponse d'erreur `application/problem+json` (§2).
 *
 * Hérite d'[IOException] pour pouvoir être levée depuis un `Interceptor` OkHttp (seules les
 * `IOException` traversent proprement la chaîne) et remonter telle quelle au code suspend appelant.
 *
 * On distingue les sous-types utiles à l'UX ; les statuts non spécialisés tombent dans [Generic].
 */
sealed class ApiException(
    val statusCode: Int,
    val problem: ProblemDetailsDto?,
    message: String?,
    cause: Throwable? = null
) : IOException(message, cause) {

    /** `X-Request-Id` à inclure dans tout rapport de bug (§1). */
    val requestId: String? get() = problem?.requestId

    /** 400 `validation-error` — contient `errors[]` (champs Zod en faute). */
    class Validation(problem: ProblemDetailsDto?) :
        ApiException(400, problem, problem?.detail ?: "Requête invalide")

    /** 400 `bad-request` — règle métier (node/volume incohérents, productId mismatch…). */
    class BadRequest(problem: ProblemDetailsDto?) :
        ApiException(400, problem, problem?.detail ?: "Requête refusée")

    /** 401 — JWT absent / invalide / expiré. Le front doit relancer le login Google. */
    class Unauthorized(problem: ProblemDetailsDto?) :
        ApiException(401, problem, problem?.detail ?: "Session expirée")

    /**
     * 402 `payment-required` — la fonctionnalité elle-même est réservée au palier premium.
     * Depuis le sprint S2, la **synchronisation** en fait partie : c'est un état terminal, pas
     * une erreur passagère. Ne jamais le retenter en boucle — seul un changement de palier le lève.
     */
    class PaymentRequired(problem: ProblemDetailsDto?) :
        ApiException(402, problem, problem?.detail ?: "Premium requis")

    /** 403 — accès refusé. */
    class Forbidden(problem: ProblemDetailsDto?) :
        ApiException(403, problem, problem?.detail ?: "Accès refusé")

    /** 404 — ressource inexistante ou appartenant à autrui (pas de leak). */
    class NotFound(problem: ProblemDetailsDto?) :
        ApiException(404, problem, problem?.detail ?: "Introuvable")

    /** 409 — doublon (même tome série+volume) ou invitation épuisée. */
    class Conflict(problem: ProblemDetailsDto?) :
        ApiException(409, problem, problem?.detail ?: "Conflit")

    /**
     * 409 `technical-limit` — plafond **technique** anti-emballement atteint côté serveur.
     *
     * Ce n'est pas une limite de palier : elle est identique pour tous les comptes et payer ne la
     * lève pas (c'est pourquoi le backend répond 409 et non 402). Elle a remplacé l'ancien
     * `402 quota-exceeded` du free tier, disparu avec les quotas au sprint S2.
     */
    class TechnicalLimit(problem: ProblemDetailsDto?) :
        ApiException(409, problem, problem?.detail ?: "Plafond technique atteint")

    /** 410 — invitation périmée. */
    class Gone(problem: ProblemDetailsDto?) :
        ApiException(410, problem, problem?.detail ?: "Expiré")

    /** 413 — corps > 256 kB. */
    class PayloadTooLarge(problem: ProblemDetailsDto?) :
        ApiException(413, problem, problem?.detail ?: "Contenu trop volumineux")

    /** 429 — rate limit dépassé. */
    class TooManyRequests(problem: ProblemDetailsDto?) :
        ApiException(429, problem, problem?.detail ?: "Trop de requêtes")

    /** 502/503 — API externe (Discogs/MAL/TMDB) indisponible ou non configurée. */
    class Upstream(statusCode: Int, problem: ProblemDetailsDto?) :
        ApiException(statusCode, problem, problem?.detail ?: "Service externe indisponible")

    /** Tout autre statut HTTP non spécialisé (dont 500). */
    class Generic(statusCode: Int, problem: ProblemDetailsDto?, message: String?) :
        ApiException(statusCode, problem, message ?: problem?.detail ?: "Erreur serveur")

    companion object {
        /** Construit la sous-classe adaptée à partir du statut HTTP et du corps problem+json. */
        fun from(statusCode: Int, problem: ProblemDetailsDto?, fallbackMessage: String? = null): ApiException =
            when (statusCode) {
                400 -> if (problem?.typeSlug == "bad-request") BadRequest(problem) else Validation(problem)
                401 -> Unauthorized(problem)
                402 -> PaymentRequired(problem)
                403 -> Forbidden(problem)
                404 -> NotFound(problem)
                409 -> if (problem?.typeSlug == "technical-limit") TechnicalLimit(problem) else Conflict(problem)
                410 -> Gone(problem)
                413 -> PayloadTooLarge(problem)
                429 -> TooManyRequests(problem)
                502, 503 -> Upstream(statusCode, problem)
                else -> Generic(statusCode, problem, fallbackMessage)
            }
    }
}
