package app.acervatim.domain.model.sync

import java.time.Instant

/** État observable de la synchronisation, pour piloter l'UI (bouton « Synchroniser », bandeau). */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Success(val at: Instant, val pushed: Int, val pulled: Int) : SyncStatus

    /**
     * Sync partielle : le quota serveur a été atteint pendant le push. [skipped] entités restent
     * `DIRTY`/`LOCAL_ONLY` et seront repoussées quand le quota le permettra. Le pull a bien eu lieu.
     */
    data class QuotaReached(val at: Instant, val pushed: Int, val pulled: Int, val skipped: Int) : SyncStatus

    data class Failure(val message: String) : SyncStatus
}
