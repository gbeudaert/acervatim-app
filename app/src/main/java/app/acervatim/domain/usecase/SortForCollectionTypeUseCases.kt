package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSortForCollectionTypeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(type: CollectionType): Flow<RecordSort> = repository.observeSort(type)
}

class SetSortForCollectionTypeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(type: CollectionType, sort: RecordSort) {
        repository.setSort(type, sort)
    }
}
