package app.acervatim.domain.usecase

import app.acervatim.domain.repository.RecordRepository
import javax.inject.Inject

/**
 * Annule le geste d'écoute d'un item : date effacée **et** compteur remis à zéro. Garder
 * un `playCount` derrière une dernière écoute effacée laisserait un état incohérent.
 */
class ResetLastPlayedUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(id: String) {
        repository.resetPlayback(id)
    }
}
