package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.repository.RecordRepository
import java.time.Clock
import javax.inject.Inject

class UpdateRatingUseCase @Inject constructor(
    private val repository: RecordRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(id: String, rating: Int?) {
        require(rating == null || rating in 0..5) { "rating must be null or in 0..5" }
        val normalized = if (rating == 0) null else rating
        repository.updateRating(id, normalized, clock.instant())
    }
}