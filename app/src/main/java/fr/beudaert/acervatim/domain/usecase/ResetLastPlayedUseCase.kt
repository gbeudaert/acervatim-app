package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.repository.RecordRepository
import javax.inject.Inject

class ResetLastPlayedUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(id: String) {
        repository.updateLastPlayed(id, ts = null)
    }
}