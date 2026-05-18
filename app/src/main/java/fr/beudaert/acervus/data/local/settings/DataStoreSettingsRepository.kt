package fr.beudaert.acervus.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import fr.beudaert.acervus.domain.repository.SettingsRepository
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

    private companion object {
        val KEY_DUPLICATE_ALBUMS_PER_ARTIST = booleanPreferencesKey("duplicate_albums_per_artist")
        const val DEFAULT_DUPLICATE = true
    }
}
