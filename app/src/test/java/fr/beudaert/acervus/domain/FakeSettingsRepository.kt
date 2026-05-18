package fr.beudaert.acervus.domain

import fr.beudaert.acervus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(
    initialDuplicate: Boolean = true
) : SettingsRepository {

    private val duplicateFlag = MutableStateFlow(initialDuplicate)

    override fun observeDuplicateAlbumsPerArtist(): Flow<Boolean> = duplicateFlag.asStateFlow()

    override suspend fun setDuplicateAlbumsPerArtist(value: Boolean) {
        duplicateFlag.value = value
    }
}
