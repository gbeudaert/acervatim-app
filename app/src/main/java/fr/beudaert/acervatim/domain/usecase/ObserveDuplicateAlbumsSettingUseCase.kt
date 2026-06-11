package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDuplicateAlbumsSettingUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.observeDuplicateAlbumsPerArtist()
}

class SetDuplicateAlbumsSettingUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(value: Boolean) {
        repository.setDuplicateAlbumsPerArtist(value)
    }
}
