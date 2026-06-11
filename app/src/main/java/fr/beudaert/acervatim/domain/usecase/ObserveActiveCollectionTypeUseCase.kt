package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveActiveCollectionTypeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<CollectionType> = settingsRepository.observeActiveCollectionType()
}
