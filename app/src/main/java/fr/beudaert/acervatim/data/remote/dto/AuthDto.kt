package fr.beudaert.acervatim.data.remote.dto

import kotlinx.serialization.Serializable

/** Corps de `POST /v1/auth/google` (§3). */
@Serializable
data class GoogleLoginRequest(
    val idToken: String
)

/** Réponse `200` de `POST /v1/auth/google` : JWT applicatif + identifiant utilisateur. */
@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val userId: String
)

/** `GET /v1/me` — profil + statut premium (§4). */
@Serializable
data class MeResponse(
    val userId: String,
    val createdAt: String,
    val premium: PremiumDto = PremiumDto()
)

@Serializable
data class PremiumDto(
    val isPremium: Boolean = false,
    /** `"grant"` | `"subscription"` | `"none"`. */
    val source: String = "none",
    /** Epoch ms ou `null` (pas premium / grant permanent). */
    val expiresAt: Long? = null
)

/**
 * `GET /v1/me/quota` (§4). `max == null` ⇒ illimité (compte premium) : ne pas afficher de jauge,
 * ne pas bloquer.
 */
@Serializable
data class QuotaResponse(
    val collections: QuotaBucketDto = QuotaBucketDto(),
    val items: QuotaBucketDto = QuotaBucketDto()
)

@Serializable
data class QuotaBucketDto(
    val used: Int = 0,
    val max: Int? = null
)

/** `GET /health` (§12). */
@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String
)
