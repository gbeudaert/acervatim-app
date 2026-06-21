package app.acervatim.domain.usecase

import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFilteredRecordsUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(filter: RecordFilter): Flow<List<Record>> =
        repository.observeFiltered(filter)
}