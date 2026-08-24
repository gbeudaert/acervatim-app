package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDisplayModeForCollectionTypeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(type: CollectionType): Flow<DisplayMode> =
        repository.observeDisplayMode(type)
}

class SetDisplayModeForCollectionTypeUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(type: CollectionType, mode: DisplayMode) {
        repository.setDisplayMode(type, mode)
    }
}
