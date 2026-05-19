package fr.beudaert.acervus.domain

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository(
    initialDuplicate: Boolean = true,
    initialActiveCollectionType: CollectionType = CollectionType.VINYL
) : SettingsRepository {

    private val duplicateFlag = MutableStateFlow(initialDuplicate)
    private val activeType = MutableStateFlow(initialActiveCollectionType)

    override fun observeDuplicateAlbumsPerArtist(): Flow<Boolean> = duplicateFlag.asStateFlow()

    override suspend fun setDuplicateAlbumsPerArtist(value: Boolean) {
        duplicateFlag.value = value
    }

    override fun observeActiveCollectionType(): Flow<CollectionType> = activeType.asStateFlow()

    override suspend fun setActiveCollectionType(value: CollectionType) {
        activeType.value = value
    }
}
