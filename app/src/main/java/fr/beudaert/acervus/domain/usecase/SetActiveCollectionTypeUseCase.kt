package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.repository.SettingsRepository
import javax.inject.Inject

class SetActiveCollectionTypeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(value: CollectionType) {
        settingsRepository.setActiveCollectionType(value)
    }
}
