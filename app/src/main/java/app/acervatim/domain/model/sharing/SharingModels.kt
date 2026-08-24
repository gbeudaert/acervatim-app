package app.acervatim.domain.model.sharing

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import java.time.Instant

/**
 * Modèles du partage de collection, alignés sur le contrat serveur du sprint S5.
 *
 * Un partage ne porte plus **une** collection avec une portée (`all`/`owned`/`wantlist`) mais **N**
 * collections, chacune avec l'ensemble de statuts qu'elle laisse voir. Les trois anciennes portées
 * ne survivent que comme raccourcis d'interface ([ShareScopePreset]) au-dessus de cet ensemble : le
 * serveur ne connaît que les statuts.
 */

/**
 * Ordre canonique des statuts, identique à celui du serveur (`ITEM_STATUSES`).
 *
 * Sert à normaliser un ensemble avant de l'envoyer ou de l'afficher : `{WISHLIST, OWNED}` et
 * `{OWNED, WISHLIST}` doivent produire la même liste, sinon deux partages identiques s'afficheraient
 * différemment selon l'ordre de cochage.
 */
val SHARE_STATUS_ORDER: List<ItemStatus> =
    listOf(ItemStatus.OWNED, ItemStatus.WISHLIST, ItemStatus.IGNORED)

/** Remet un ensemble de statuts dans l'ordre canonique. */
fun Set<ItemStatus>.canonical(): List<ItemStatus> = SHARE_STATUS_ORDER.filter { it in this }

/**
 * Raccourcis d'interface, **côté app uniquement**. Le serveur n'en sait rien : ils ne servent qu'à
 * proposer les trois combinaisons courantes en un geste, l'utilisateur restant libre de cocher
 * n'importe quel ensemble non vide — à commencer par « possédés **et** désirés, sans les ignorés »,
 * que les anciennes portées interdisaient.
 */
enum class ShareScopePreset(val statuses: Set<ItemStatus>) {
    ALL(ItemStatus.entries.toSet()),
    OWNED(setOf(ItemStatus.OWNED)),
    WANTLIST(setOf(ItemStatus.WISHLIST));

    companion object {
        /** Le raccourci correspondant exactement à cet ensemble, ou `null` (choix libre). */
        fun matching(statuses: Set<ItemStatus>): ShareScopePreset? =
            entries.firstOrNull { it.statuses == statuses }
    }
}

/**
 * Une collection locale candidate au partage : elle a un `id` serveur, donc elle a été synchronisée.
 *
 * Une collection jamais synchronisée n'est pas partageable — le partage porte sur la copie serveur,
 * pas sur la base Room. Elle n'apparaît donc simplement pas dans le sélecteur.
 */
data class SharableCollection(
    /** `id` **serveur**, seul identifiant que connaisse le contrat de partage. */
    val collectionId: String,
    val name: String,
    val typeCode: String
) {
    val type: CollectionType? get() = CollectionType.fromTypeCode(typeCode)
}

/** Une collection exposée par un partage, avec les statuts qu'elle laisse voir. */
data class ShareEntry(
    val collectionId: String,
    val name: String,
    val typeCode: String,
    val statuses: Set<ItemStatus>
) {
    val type: CollectionType? get() = CollectionType.fromTypeCode(typeCode)

    /** Le raccourci correspondant, si l'ensemble en est un — sinon l'UI liste les statuts. */
    val preset: ShareScopePreset? get() = ShareScopePreset.matching(statuses)
}

/**
 * Idem côté **membre**, avec le nombre d'éléments qu'il verra effectivement.
 *
 * Le propriétaire n'a pas ce compte : le serveur ne le lui calcule pas (une requête par entrée sur
 * une liste de partages non plafonnée). Ne pas le recalculer côté app — il dépend des statuts, que
 * le client ne filtre pas.
 */
data class ReceivedEntry(
    val collectionId: String,
    val name: String,
    val typeCode: String,
    val statuses: Set<ItemStatus>,
    val itemCount: Int
) {
    val type: CollectionType? get() = CollectionType.fromTypeCode(typeCode)
    val preset: ShareScopePreset? get() = ShareScopePreset.matching(statuses)
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
 *
 * [label] est l'aide-mémoire **du propriétaire** (« Wantlist manga avec Alice »). Il ne franchit
 * jamais la frontière : les membres ne le voient pas, et le libellé qu'eux donnent au partage ne
 * remonte pas ici.
 */
data class CollectionShare(
    val id: String,
    val label: String?,
    val maxUses: Int,
    val usedCount: Int,
    val expiresAt: Instant?,
    val createdAt: Instant,
    val collections: List<ShareEntry> = emptyList(),
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
    val share: CollectionShare,
    val code: String
)

/**
 * Un partage que **j'ai rejoint**. [label] est le mien (« Wantlist manga de Bob ») : le propriétaire
 * ne le voit pas, et le sien ne descend jamais jusqu'ici.
 *
 * Il n'y a pas de « quitter un partage » : seul le propriétaire révoque. C'est le contrat serveur,
 * l'UI ne doit donc pas proposer une action qui n'existe pas.
 */
data class ReceivedShare(
    val shareId: String,
    val label: String?,
    val redeemedAt: Instant,
    val collections: List<ReceivedEntry> = emptyList()
)

/** Retour de `redeem` : le partage rejoint, plus le fait que l'adhésion existait déjà. */
data class RedeemedShare(
    val share: ReceivedShare,
    /** `true` si j'étais déjà membre : le redeem est idempotent, il n'a consommé aucune place. */
    val alreadyMember: Boolean
)

/** Cause d'échec d'une opération de partage, telle que l'écran a besoin de la distinguer. */
enum class SharingFailure {
    /** 402 : émettre un partage est premium, et le statut a pu expirer entre l'affichage et l'action. */
    PREMIUM_REQUIRED,

    /**
     * Le code saisi ne vaut pas : inexistant, révoqué, expiré, épuisé, ou saisi par un membre
     * éjecté. Le serveur ne distingue **pas** ces cas (un seul statut, un seul message) — c'est
     * délibéré : répondre plus finement ferait du redeem un oracle. L'UI ne doit donc pas inventer
     * un diagnostic plus précis qu'elle n'en a.
     */
    CODE_INVALID,

    /** 400 : c'est son propre partage. Le seul refus que le serveur nomme, et pour cause. */
    OWN_SHARE,

    /** 429 : trop de tentatives de code en peu de temps (bucket resserré côté serveur). */
    TOO_MANY_ATTEMPTS,

    /** 404 : la ressource a disparu entre l'affichage et l'action (partage révoqué, membre éjecté). */
    GONE,

    /**
     * 404 **à la création** : le serveur ne reconnaît pas l'une des collections envoyées.
     *
     * Distinct de [GONE], et pas par élégance : à la création il n'y a pas encore de partage, donc
     * « ce partage n'existe plus » n'a aucun sens. Ce que ça veut dire est que l'app détient un
     * `remoteId` périmé — collection supprimée côté serveur, ou compte changé — et le seul geste
     * utile est de resynchroniser. Le serveur ne dit pas **laquelle** (ce serait un test d'existence).
     */
    COLLECTION_GONE,

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
