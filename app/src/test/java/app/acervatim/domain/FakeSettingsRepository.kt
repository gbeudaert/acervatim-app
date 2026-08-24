package app.acervatim.domain

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.defaultDisplayMode
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeSettingsRepository(
    initialDuplicate: Boolean = true,
    initialActiveCollectionType: CollectionType = CollectionType.VINYL,
    initialListModes: Map<CollectionType, ListMode> = emptyMap(),
    initialDisplayModes: Map<CollectionType, DisplayMode> = emptyMap(),
    initialVisibleTypes: Set<CollectionType> = CollectionType.entries.toSet(),
    initialSyncedTypes: Set<CollectionType> = CollectionType.entries.toSet()
) : SettingsRepository {

    private val duplicateFlag = MutableStateFlow(initialDuplicate)
    private val activeType = MutableStateFlow(initialActiveCollectionType)
    private val listModes = MutableStateFlow(initialListModes)
    private val displayModes = MutableStateFlow(initialDisplayModes)
    private val visibleTypes = MutableStateFlow(initialVisibleTypes)
    private val syncedTypes = MutableStateFlow(initialSyncedTypes)

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

    override fun observeDisplayMode(type: CollectionType): Flow<DisplayMode> =
        displayModes.map { it[type] ?: type.defaultDisplayMode }

    override suspend fun setDisplayMode(type: CollectionType, mode: DisplayMode) {
        displayModes.value = displayModes.value + (type to mode)
    }

    private val sorts = MutableStateFlow<Map<CollectionType, RecordSort>>(emptyMap())

    override fun observeSort(type: CollectionType): Flow<RecordSort> =
        sorts.map { it[type] ?: RecordSort.DEFAULT }

    override suspend fun setSort(type: CollectionType, sort: RecordSort) {
        sorts.value = sorts.value + (type to sort)
    }

    override fun observeVisibleCollectionTypes(): Flow<Set<CollectionType>> = visibleTypes.asStateFlow()

    override suspend fun setCollectionTypeVisible(type: CollectionType, visible: Boolean) {
        val updated = if (visible) visibleTypes.value + type else visibleTypes.value - type
        if (updated.isNotEmpty()) {
            visibleTypes.value = updated
        }
    }

    override fun observeSyncedCollectionTypes(): Flow<Set<CollectionType>> = syncedTypes.asStateFlow()

    override suspend fun setCollectionTypeSynced(type: CollectionType, synced: Boolean) {
        syncedTypes.value = if (synced) syncedTypes.value + type else syncedTypes.value - type
    }
}
