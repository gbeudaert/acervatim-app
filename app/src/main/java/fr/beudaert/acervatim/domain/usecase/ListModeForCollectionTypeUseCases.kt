package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.ListMode
import fr.beudaert.acervatim.domain.repository.SettingsRepository
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
