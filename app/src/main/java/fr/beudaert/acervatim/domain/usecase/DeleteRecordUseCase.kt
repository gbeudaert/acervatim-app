package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.repository.RecordRepository
import javax.inject.Inject

class DeleteRecordUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteById(id)
    }
}