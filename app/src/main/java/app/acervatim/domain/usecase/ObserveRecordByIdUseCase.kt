package app.acervatim.domain.usecase

import app.acervatim.domain.model.Record
import app.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecordByIdUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(id: String): Flow<Record?> = repository.observeById(id)
}