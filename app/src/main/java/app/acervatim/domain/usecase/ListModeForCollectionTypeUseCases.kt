package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveListModeForCollectionTypeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(type: CollectionType): Flow<ListMode> =
        repository.observeListMode(type)
}

class SetListModeForCollectionTypeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(type: CollectionType, mode: ListMode) {
        repository.setListMode(type, mode)
    }
}
