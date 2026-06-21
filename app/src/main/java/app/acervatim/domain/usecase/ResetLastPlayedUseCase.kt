package app.acervatim.domain.usecase

import app.acervatim.domain.repository.RecordRepository
import javax.inject.Inject

class ResetLastPlayedUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(id: String) {
        repository.updateLastPlayed(id, ts = null)
    }
}