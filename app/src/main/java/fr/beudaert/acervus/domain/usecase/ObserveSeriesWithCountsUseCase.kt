package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.SeriesWithCounts
import fr.beudaert.acervus.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSeriesWithCountsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    operator fun invoke(collectionType: CollectionType): Flow<List<SeriesWithCounts>> =
        repository.observeWithCountsByCollectionType(collectionType)
}
