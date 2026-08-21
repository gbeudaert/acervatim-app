package app.acervatim.domain.model.sync

import java.time.Instant

/** État observable de la synchronisation, pour piloter l'UI (bouton « Synchroniser », bandeau). */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Success(val at: Instant, val pushed: Int, val pulled: Int) : SyncStatus

    /**
     * Sync partielle : le **plafond technique** serveur (409 `technical-limit`) a été atteint
     * pendant le push. [skipped] entités restent `DIRTY`/`LOCAL_ONLY` et repartiront plus tard.
     * Le pull a bien eu lieu.
     *
     * Ce plafond est un garde-fou anti-emballement, identique pour tous les comptes : il ne se
     * lève pas en payant, et le backend ne le communique pas comme une limite d'offre. D'où un
     * message neutre côté UI — surtout pas un appel à souscrire.
     */
    data class LimitReached(val at: Instant, val pushed: Int, val pulled: Int, val skipped: Int) : SyncStatus

    /**
     * État **terminal** : le serveur a répondu 402 `payment-required`. La synchronisation fait
     * partie du palier premium (sprint S2) ; rien ne sert de retenter, seul un changement de
     * palier débloque. Les données locales restent intactes — l'usage hors-ligne n'est pas affecté.
     */
    data class PremiumRequired(val at: Instant) : SyncStatus

    data class Failure(val message: String) : SyncStatus
}
