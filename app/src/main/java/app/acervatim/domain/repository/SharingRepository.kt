package app.acervatim.domain.repository

import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.domain.model.sharing.RedeemedShare
import app.acervatim.domain.model.sharing.SharableCollection
import java.time.Instant

/**
 * Partages de collection (`/v1/shares/…`), les deux sens : ceux que j'émets et ceux que je rejoins.
 *
 * Tout est distant : rien n'est mis en cache localement, et surtout pas le code d'un partage. Les
 * méthodes renvoient un [Result] plutôt que de lever — l'écran doit savoir distinguer un 402
 * (premium expiré entre l'affichage et l'action) d'une panne réseau ou d'un code refusé.
 *
 * La **lecture** d'une collection reçue en partage n'est pas ici : elle passe par les routes de
 * lecture existantes, dont l'autorisation est étendue côté serveur (sprint S4). Voir
 * [SharedCollectionRepository].
 */
interface SharingRepository {

    // ---- Côté propriétaire -------------------------------------------------------------------

    /**
     * Collections locales ayant un `id` **serveur**, seules candidates au partage : le partage porte
     * sur la copie serveur, pas sur la base Room. Une collection jamais synchronisée n'y figure pas.
     */
    suspend fun sharableCollections(): List<SharableCollection>

    /**
     * Partages non révoqués que j'ai émis, membres actifs inclus (les expirés restent listés).
     * [collectionId] non nul ⇒ seulement ceux qui exposent cette collection.
     */
    suspend fun shares(collectionId: String? = null): Result<List<CollectionShare>>

    /**
     * Crée un partage et renvoie le code en clair — unique occasion de l'obtenir.
     *
     * [entries] doit être non vide, et chaque ensemble de statuts non vide : une collection sans
     * statut exposé n'est pas partagée, il faut alors ne pas l'inclure. Le serveur le refuse aussi.
     */
    suspend fun createShare(
        entries: Map<String, Set<ItemStatus>>,
        label: String?,
        maxUses: Int,
        expiresAt: Instant?
    ): Result<CreatedShare>

    /**
     * Renomme un partage émis. Seul champ modifiable après coup : changer les collections ou les
     * statuts d'un partage vivant modifierait sans le dire ce que ses membres voient déjà.
     *
     * [label] vide ou blanc ⇒ le libellé est effacé.
     */
    suspend fun renameShare(shareId: String, label: String): Result<CollectionShare>

    /** Révoque le partage entier : le code cesse de prendre et tous ses membres perdent l'accès. */
    suspend fun revokeShare(shareId: String): Result<Unit>

    /** Éjecte un membre ; le code reste valide pour les autres, mais pas pour lui. */
    suspend fun revokeMember(shareId: String, memberUserId: String): Result<Unit>

    // ---- Côté membre -------------------------------------------------------------------------

    /**
     * Consomme un code. **Ne demande pas le premium** : c'est le propriétaire qui paie le stockage.
     * Idempotent côté serveur — re-saisir un code déjà utilisé ne consomme pas de place.
     */
    suspend fun redeem(code: String, label: String?): Result<RedeemedShare>

    /** Partages que j'ai rejoints et qui vivent encore, avec **mon** libellé. */
    suspend fun receivedShares(): Result<List<ReceivedShare>>

    /** Renomme, pour moi seul, un partage rejoint. Le propriétaire ne verra pas ce nom. */
    suspend fun renameReceivedShare(shareId: String, label: String): Result<ReceivedShare>
}
