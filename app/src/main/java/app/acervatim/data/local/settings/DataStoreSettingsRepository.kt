package app.acervatim.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override fun observeDuplicateAlbumsPerArtist(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_DUPLICATE_ALBUMS_PER_ARTIST] ?: DEFAULT_DUPLICATE }

    override suspend fun setDuplicateAlbumsPerArtist(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DUPLICATE_ALBUMS_PER_ARTIST] = value
        }
    }

    override fun observeActiveCollectionType(): Flow<CollectionType> =
        dataStore.data.map { prefs ->
            val raw = prefs[KEY_ACTIVE_COLLECTION_TYPE]
            raw?.let { runCatching { CollectionType.valueOf(it) }.getOrNull() } ?: DEFAULT_ACTIVE_TYPE
        }

    override suspend fun setActiveCollectionType(value: CollectionType) {
        dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_COLLECTION_TYPE] = value.name
        }
    }

    override fun observeListMode(type: CollectionType): Flow<ListMode> {
        val key = listModeKey(type)
        return dataStore.data.map { prefs ->
            val raw = prefs[key]
            raw?.let { runCatching { ListMode.valueOf(it) }.getOrNull() } ?: type.defaultListMode
        }
    }

    override suspend fun setListMode(type: CollectionType, mode: ListMode) {
        dataStore.edit { prefs ->
            prefs[listModeKey(type)] = mode.name
        }
    }

    override fun observeVisibleCollectionTypes(): Flow<Set<CollectionType>> =
        dataStore.data.map { prefs ->
            val raw = prefs[KEY_VISIBLE_COLLECTION_TYPES]
            // Clé absente ⇒ tout est visible (défaut). On filtre les noms inconnus (types retirés)
            // et on garantit au moins un type visible : un set persisté vide retombe sur le défaut.
            val resolved = raw
                ?.mapNotNull { name -> runCatching { CollectionType.valueOf(name) }.getOrNull() }
                ?.toSet()
            resolved?.takeIf { it.isNotEmpty() } ?: ALL_TYPES
        }

    override suspend fun setCollectionTypeVisible(type: CollectionType, visible: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_VISIBLE_COLLECTION_TYPES]
                ?.mapNotNull { name -> runCatching { CollectionType.valueOf(name) }.getOrNull() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: ALL_TYPES
            val updated = if (visible) current + type else current - type
            // On ne persiste jamais un set vide : au moins un type doit rester affiché.
            if (updated.isNotEmpty()) {
                prefs[KEY_VISIBLE_COLLECTION_TYPES] = updated.map { it.name }.toSet()
            }
        }
    }

    private fun listModeKey(type: CollectionType) =
        stringPreferencesKey("list_mode_${type.name}")

    private companion object {
        val KEY_DUPLICATE_ALBUMS_PER_ARTIST = booleanPreferencesKey("duplicate_albums_per_artist")
        val KEY_ACTIVE_COLLECTION_TYPE = stringPreferencesKey("active_collection_type")
        val KEY_VISIBLE_COLLECTION_TYPES = stringSetPreferencesKey("visible_collection_types")
        const val DEFAULT_DUPLICATE = true
        val DEFAULT_ACTIVE_TYPE = CollectionType.VINYL
        val ALL_TYPES = CollectionType.entries.toSet()
    }
}
