package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSeriesByIdUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    operator fun invoke(id: String): Flow<Series?> = repository.observeById(id)
}
