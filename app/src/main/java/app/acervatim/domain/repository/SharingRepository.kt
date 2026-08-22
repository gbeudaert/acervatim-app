package app.acervatim.domain.repository

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ShareScope
import java.time.Instant

/**
 * Partages émis par le propriétaire d'une collection (`/v1/collections/:id/shares`, `/v1/shares/…`).
 *
 * Tout est distant : rien n'est mis en cache localement, et surtout pas le code d'un partage. Les
 * méthodes renvoient un [Result] plutôt que de lever — l'écran doit savoir distinguer un 402
 * (premium expiré entre l'affichage et l'action) d'une panne réseau.
 *
 * La lecture d'une collection reçue en partage n'est pas ici : elle passe par les routes de lecture
 * existantes, dont l'autorisation est étendue côté serveur (sprint S4 / A4).
 */
interface SharingRepository {

    /**
     * `id` **serveur** de la collection locale de ce type, ou `null` si elle n'a jamais été
     * synchronisée. Sans lui, il n'y a rien à partager : le partage porte sur la collection
     * distante, pas sur la base locale.
     */
    suspend fun remoteCollectionId(type: CollectionType): String?

    /** Partages non révoqués de la collection, membres actifs inclus (les expirés restent listés). */
    suspend fun shares(collectionId: String): Result<List<CollectionShare>>

    /** Crée un partage et renvoie le code en clair — unique occasion de l'obtenir. */
    suspend fun createShare(
        collectionId: String,
        scope: ShareScope,
        maxUses: Int,
        expiresAt: Instant?
    ): Result<CreatedShare>

    /** Révoque le partage entier : le code cesse de prendre et tous ses membres perdent l'accès. */
    suspend fun revokeShare(shareId: String): Result<Unit>

    /** Éjecte un membre ; le code reste valide pour les autres, mais pas pour lui. */
    suspend fun revokeMember(shareId: String, memberUserId: String): Result<Unit>
}
