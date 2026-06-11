package fr.beudaert.acervatim.domain

import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.ListMode
import fr.beudaert.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeSettingsRepository(
    initialDuplicate: Boolean = true,
    initialActiveCollectionType: CollectionType = CollectionType.VINYL,
    initialListModes: Map<CollectionType, ListMode> = emptyMap()
) : SettingsRepository {

    private val duplicateFlag = MutableStateFlow(initialDuplicate)
    private val activeType = MutableStateFlow(initialActiveCollectionType)
    private val listModes = MutableStateFlow(initialListModes)

    override fun observeDuplicateAlbumsPerArtist(): Flow<Boolean> = duplicateFlag.asStateFlow()

    override suspend fun setDuplicateAlbumsPerArtist(value: Boolean) {
        duplicateFlag.value = value
    }

    override fun observeActiveCollectionType(): Flow<CollectionType> = activeType.asStateFlow()

    override suspend fun setActiveCollectionType(value: CollectionType) {
        activeType.value = value
    }

    override fun observeListMode(type: CollectionType): Flow<ListMode> =
        listModes.map { it[type] ?: type.defaultListMode }

    override suspend fun setListMode(type: CollectionType, mode: ListMode) {
        listModes.value = listModes.value + (type to mode)
    }
}
