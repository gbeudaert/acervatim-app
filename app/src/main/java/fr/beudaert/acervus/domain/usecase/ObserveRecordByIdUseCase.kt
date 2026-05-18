package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecordByIdUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(id: String): Flow<Record?> = repository.observeById(id)
}