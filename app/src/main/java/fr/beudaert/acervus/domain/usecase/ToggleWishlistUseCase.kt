package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.RecordStatus
import fr.beudaert.acervus.domain.repository.RecordRepository
import java.time.Clock
import javax.inject.Inject

class ToggleWishlistUseCase @Inject constructor(
    private val repository: RecordRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(id: String, wishlist: Boolean) {
        val newStatus = if (wishlist) RecordStatus.WISHLIST else RecordStatus.OWNED
        repository.updateStatus(id, newStatus, clock.instant())
    }
}