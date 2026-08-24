package app.acervatim.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.RecordSortField
import app.acervatim.domain.model.SortDirection
import app.acervatim.domain.model.defaultDisplayMode
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

    override fun observeDisplayMode(type: CollectionType): Flow<DisplayMode> {
        val key = displayModeKey(type)
        return dataStore.data.map { prefs ->
            val raw = prefs[key]
            raw?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() } ?: type.defaultDisplayMode
        }
    }

    override suspend fun setDisplayMode(type: CollectionType, mode: DisplayMode) {
        dataStore.edit { prefs ->
            prefs[displayModeKey(type)] = mode.name
        }
    }

    override fun observeSort(type: CollectionType): Flow<RecordSort> {
        val key = sortKey(type)
        return dataStore.data.map { prefs -> parseSort(prefs[key]) }
    }

    override suspend fun setSort(type: CollectionType, sort: RecordSort) {
        dataStore.edit { prefs ->
            prefs[sortKey(type)] = "${sort.field.name}:${sort.direction.name}"
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

    override fun observeSyncedCollectionTypes(): Flow<Set<CollectionType>> =
        dataStore.data.map { prefs ->
            // Clé absente ⇒ tout est synchronisé (défaut). On distingue « absent » (défaut) d'un set
            // persisté vide (l'utilisateur a tout retiré) via un marqueur, car un stringSet vide et
            // une clé absente sont indiscernables en DataStore : on stocke donc le marqueur MARKER_NONE.
            val raw = prefs[KEY_SYNCED_COLLECTION_TYPES] ?: return@map ALL_TYPES
            if (raw == MARKER_NONE) {
                emptySet()
            } else {
                raw.mapNotNull { name -> runCatching { CollectionType.valueOf(name) }.getOrNull() }.toSet()
            }
        }

    override suspend fun setCollectionTypeSynced(type: CollectionType, synced: Boolean) {
        dataStore.edit { prefs ->
            val current = when (val raw = prefs[KEY_SYNCED_COLLECTION_TYPES]) {
                null -> ALL_TYPES
                MARKER_NONE -> emptySet()
                else -> raw.mapNotNull { name -> runCatching { CollectionType.valueOf(name) }.getOrNull() }.toSet()
            }
            val updated = if (synced) current + type else current - type
            // Un ensemble vide est autorisé (tout garder local) : on le matérialise par MARKER_NONE
            // pour le distinguer du défaut « clé absente = tout synchronisé ».
            prefs[KEY_SYNCED_COLLECTION_TYPES] =
                if (updated.isEmpty()) MARKER_NONE else updated.map { it.name }.toSet()
        }
    }

    private fun listModeKey(type: CollectionType) =
        stringPreferencesKey("list_mode_${type.name}")

    private fun displayModeKey(type: CollectionType) =
        stringPreferencesKey("display_mode_${type.name}")

    private fun sortKey(type: CollectionType) =
        stringPreferencesKey("sort_${type.name}")

    /**
     * `CHAMP:SENS`, deux énumérations plutôt qu'un entier ordinal : réordonner
     * [RecordSortField] un jour ne doit pas transformer silencieusement le tri de tout le monde.
     * Toute valeur illisible retombe sur le tri par défaut.
     */
    private fun parseSort(raw: String?): RecordSort {
        val parts = raw?.split(':') ?: return RecordSort.DEFAULT
        if (parts.size != 2) return RecordSort.DEFAULT
        val field = runCatching { RecordSortField.valueOf(parts[0]) }.getOrNull()
            ?: return RecordSort.DEFAULT
        val direction = runCatching { SortDirection.valueOf(parts[1]) }.getOrNull()
            ?: return RecordSort.DEFAULT
        return RecordSort(field, direction)
    }

    private companion object {
        val KEY_DUPLICATE_ALBUMS_PER_ARTIST = booleanPreferencesKey("duplicate_albums_per_artist")
        val KEY_ACTIVE_COLLECTION_TYPE = stringPreferencesKey("active_collection_type")
        val KEY_VISIBLE_COLLECTION_TYPES = stringSetPreferencesKey("visible_collection_types")
        val KEY_SYNCED_COLLECTION_TYPES = stringSetPreferencesKey("synced_collection_types")
        const val DEFAULT_DUPLICATE = true
        val DEFAULT_ACTIVE_TYPE = CollectionType.VINYL
        val ALL_TYPES = CollectionType.entries.toSet()
        // Marqueur « aucun type synchronisé » : un nom qui n'est pas un CollectionType valide, donc
        // jamais confondu avec une valeur réelle et ignoré par les lectures tolérantes ailleurs.
        val MARKER_NONE = setOf("__none__")
    }
}
