package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.RecordFilter
import fr.beudaert.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFilteredRecordsUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(filter: RecordFilter): Flow<List<Record>> =
        repository.observeFiltered(filter)
}