package app.acervatim.domain.usecase

import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSeriesByIdUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    operator fun invoke(id: String): Flow<Series?> = repository.observeById(id)
}
