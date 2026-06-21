package app.acervatim.domain.usecase

import app.acervatim.domain.repository.RecordRepository
import java.time.Clock
import javax.inject.Inject

class MarkAsPlayedUseCase @Inject constructor(
    private val repository: RecordRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(id: String) {
        repository.updateLastPlayed(id, clock.instant())
    }
}