package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFilteredRecordsUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(filter: RecordFilter): Flow<List<Record>> =
        repository.observeFiltered(filter)
}