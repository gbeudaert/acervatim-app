package app.acervatim.domain.model.sharing

import java.time.Instant

/**
 * Ce qu'un membre voit de la collection partagée. Le filtrage est **serveur-side** : la portée est
 * choisie à la création du partage et n'est jamais fournie par le client en lecture.
 */
enum class ShareScope(val code: String) {
    ALL("all"),
    OWNED("owned"),
    WANTLIST("wantlist");

    companion object {
        /** Portée inconnue (backend en avance sur l'app) → [ALL], la seule valeur non trompeuse. */
        fun fromCode(code: String): ShareScope =
            entries.firstOrNull { it.code == code.lowercase() } ?: ALL
    }
}

/**
 * Membre ayant rejoint un partage. [memberUserId] est opaque et le restera : les comptes sont
 * anonymes en base (aucun email, aucun nom). Ne rien afficher qui suggérerait une identité.
 */
data class ShareMember(
    val memberUserId: String,
    val redeemedAt: Instant
)

/** État affichable d'un partage, dérivé (le serveur ne renvoie pas de champ « statut »). */
enum class ShareState { ACTIVE, EXPIRED, EXHAUSTED }

/**
 * Un partage émis par le propriétaire. **Sans le code** : il n'existe en clair qu'à la création
 * ([CreatedShare]), le serveur n'en garde que le HMAC.
 */
data class CollectionShare(
    val id: String,
    val collectionId: String,
    val scope: ShareScope,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: Instant?,
    val createdAt: Instant,
    val members: List<ShareMember> = emptyList()
) {
    /**
     * L'expiration prime sur l'épuisement : un code périmé ne reprend pas si une place se libère,
     * alors qu'un code épuisé non périmé redevient utilisable si un membre est révoqué.
     */
    fun stateAt(now: Instant): ShareState = when {
        expiresAt != null && !expiresAt.isAfter(now) -> ShareState.EXPIRED
        usedCount >= maxUses -> ShareState.EXHAUSTED
        else -> ShareState.ACTIVE
    }
}

/**
 * Retour de la création : le seul objet qui porte le code en clair. À garder **en mémoire
 * uniquement**, le temps de l'écran — jamais de DataStore, de Room, de `SavedStateHandle` ni de log.
 */
data class CreatedShare(
    val id: String,
    val code: String,
    val scope: ShareScope,
    val maxUses: Int,
    val expiresAt: Instant?
)

/** Cause d'échec d'une opération de partage, telle que l'écran a besoin de la distinguer. */
enum class SharingFailure {
    /** 402 : émettre un partage est premium, et le statut a pu expirer entre l'affichage et l'action. */
    PREMIUM_REQUIRED,

    /** Pas de réseau, ou serveur injoignable. */
    NETWORK,

    /** Tout le reste (4xx/5xx inattendu) : rien d'actionnable à dire à l'utilisateur. */
    UNKNOWN
}

/**
 * Échec d'une opération de [app.acervatim.domain.repository.SharingRepository]. La traduction depuis
 * le transport (`ApiException`, `IOException`) est faite dans la couche `data` : l'UI n'a pas à
 * connaître les statuts HTTP.
 */
class SharingException(
    val failure: SharingFailure,
    cause: Throwable? = null
) : Exception(cause?.message, cause)
