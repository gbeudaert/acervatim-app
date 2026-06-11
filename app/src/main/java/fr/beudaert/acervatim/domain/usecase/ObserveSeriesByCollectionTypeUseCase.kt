package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.Series
import fr.beudaert.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSeriesByCollectionTypeUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    operator fun invoke(collectionType: CollectionType): Flow<List<Series>> =
        repository.observeByCollectionType(collectionType)
}
