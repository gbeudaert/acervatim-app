package app.acervatim.domain.usecase

import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.repository.RecordRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Met à jour le statut d'un item (OWNED / WISHLIST / IGNORED) avec horodatage.
 * Utilisé par la bascule explicite (menu, tap long) en vue SERIAL.
 */
class UpdateItemStatusUseCase @Inject constructor(
    private val repository: RecordRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(id: String, status: ItemStatus) {
        repository.updateStatus(id, status, clock.instant())
    }
}
