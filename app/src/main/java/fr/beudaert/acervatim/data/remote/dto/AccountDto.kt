package fr.beudaert.acervatim.data.remote.dto

import kotlinx.serialization.Serializable

/** `DELETE /v1/me` (§4) — idempotent. */
@Serializable
data class DeletedResponse(val deleted: Boolean = true)

// ---- OAuth utilisateur (§10) ---------------------------------------------------------------

/** `GET /v1/oauth/:provider/start`. */
@Serializable
data class OAuthStartResponse(val authorizeUrl: String)

/** `GET /v1/oauth` — providers connectés (jamais le token). */
@Serializable
data class ConnectedProviderDto(
    val provider: String,
    /** `0` = pas d'expiration (cas OAuth 1.0a). */
    val expiresAtMs: Long = 0
)

// ---- Premium : abonnements & invitations (§11) ---------------------------------------------

/** Corps de `POST /v1/subscriptions/verify`. */
@Serializable
data class VerifySubscriptionRequest(
    val purchaseToken: String,
    val productId: String
)

/** Réponse de `POST /v1/subscriptions/verify`. */
@Serializable
data class SubscriptionDto(
    /** `active | grace_period | on_hold | paused | cancelled | expired`. */
    val status: String,
    /** Epoch ms. */
    val expiresAt: Long? = null,
    val autoRenew: Boolean = false,
    val productId: String
)

/** Corps de `POST /v1/invitations/redeem`. */
@Serializable
data class RedeemInvitationRequest(val code: String)

/** Réponse de `POST /v1/invitations/redeem`. */
@Serializable
data class RedeemInvitationResponse(
    val alreadyRedeemed: Boolean = false,
    val premiumGranted: Boolean = false,
    /** `beta_tester | comp | support | admin` (selon le code). */
    val reason: String? = null
)
