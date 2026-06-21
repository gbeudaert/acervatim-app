package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSeriesByCollectionTypeUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    operator fun invoke(collectionType: CollectionType): Flow<List<Series>> =
        repository.observeByCollectionType(collectionType)
}
