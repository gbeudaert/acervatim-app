package app.acervatim.domain.usecase

import app.acervatim.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDistinctGenresUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(): Flow<List<String>> = repository.observeDistinctGenres()
}