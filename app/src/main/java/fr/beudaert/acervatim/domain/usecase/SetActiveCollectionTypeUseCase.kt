package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.repository.SettingsRepository
import javax.inject.Inject

class SetActiveCollectionTypeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(value: CollectionType) {
        settingsRepository.setActiveCollectionType(value)
    }
}
