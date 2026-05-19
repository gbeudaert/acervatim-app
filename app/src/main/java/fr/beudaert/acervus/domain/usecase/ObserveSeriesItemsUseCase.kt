package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSeriesItemsUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(seriesId: String): Flow<List<Record>> = repository.observeBySeriesId(seriesId)
}
