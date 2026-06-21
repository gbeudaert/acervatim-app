package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.repository.SettingsRepository
import javax.inject.Inject

class SetActiveCollectionTypeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(value: CollectionType) {
        settingsRepository.setActiveCollectionType(value)
    }
}
