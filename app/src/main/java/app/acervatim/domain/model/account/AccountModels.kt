package app.acervatim.domain.model.account

import java.time.Instant

/** Origine du statut premium (`/v1/me`). */
enum class PremiumSource { NONE, GRANT, SUBSCRIPTION, UNKNOWN }

data class PremiumStatus(
    val isPremium: Boolean,
    val source: PremiumSource,
    /** `null` si pas premium, ou grant permanent. */
    val expiresAt: Instant?
)

/** Compteur de quota côté serveur. `max == null` ⇒ illimité (premium) : pas de jauge, pas de blocage. */
data class QuotaBucket(
    val used: Int,
    val max: Int?
) {
    val unlimited: Boolean get() = max == null
    val reached: Boolean get() = max != null && used >= max
    val ratio: Float? get() = max?.takeIf { it > 0 }?.let { (used.toFloat() / it).coerceIn(0f, 1f) }
}

data class QuotaInfo(
    val collections: QuotaBucket,
    val items: QuotaBucket
)

data class UserProfile(
    val userId: String,
    val createdAt: Instant,
    val premium: PremiumStatus
)

/**
 * État observable du compte backend. La règle d'or : un backend muet ne retire **aucune**
 * fonctionnalité — l'app dégrade en [Unknown] et se comporte comme avant (offline-first).
 */
sealed interface AccountInfo {
    /** Pas de session active. */
    data object SignedOut : AccountInfo

    /** Connecté, mais profil non chargé (pas encore fetché, ou `/v1/me` en erreur). */
    data object Unknown : AccountInfo

    /** Profil connu ; [quota] `null` si `/v1/me/quota` a échoué alors que `/v1/me` a répondu. */
    data class Known(
        val profile: UserProfile,
        val quota: QuotaInfo?
    ) : AccountInfo
}

/** Résultat de `POST /v1/invitations/redeem`. */
data class InvitationRedeemResult(
    val alreadyRedeemed: Boolean,
    val premiumGranted: Boolean
)
