package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.model.Series
import fr.beudaert.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSeriesByIdUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    operator fun invoke(id: String): Flow<Series?> = repository.observeById(id)
}
