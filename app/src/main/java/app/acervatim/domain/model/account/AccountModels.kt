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

    /**
     * Profil connu. Plus de relevé de quota : la notion de palier gratuit plafonné a disparu au
     * sprint S2 (`GET /v1/me/quota` retiré côté serveur). Ce qui reste côté serveur est un plafond
     * **technique** anti-emballement, identique pour tous et non communiqué.
     */
    data class Known(
        val profile: UserProfile
    ) : AccountInfo
}

/** Résultat de `POST /v1/invitations/redeem`. */
data class InvitationRedeemResult(
    val alreadyRedeemed: Boolean,
    val premiumGranted: Boolean
)
