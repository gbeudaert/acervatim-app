package app.acervatim.domain.repository

import app.acervatim.domain.model.sync.SyncStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Synchronisation des collections/items/nœuds locaux avec le backend (push/pull, last-write-wins).
 * Requiert une session active ([AuthRepository]). N'altère jamais le mode offline-first.
 */
interface SyncRepository {

    val status: StateFlow<SyncStatus>

    /**
     * Lance une synchronisation complète : push des entités locales modifiées puis pull du serveur.
     * `Result.failure` si non connecté ou en cas d'erreur réseau ; les détails sont aussi reflétés
     * dans [status].
     */
    suspend fun syncNow(): Result<Unit>
}
