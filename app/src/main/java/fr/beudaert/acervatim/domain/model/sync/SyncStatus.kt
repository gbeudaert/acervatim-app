package fr.beudaert.acervatim.domain.model.sync

import java.time.Instant

/** État observable de la synchronisation, pour piloter l'UI (bouton « Synchroniser », bandeau). */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Success(val at: Instant, val pushed: Int, val pulled: Int) : SyncStatus
    data class Failure(val message: String) : SyncStatus
}
