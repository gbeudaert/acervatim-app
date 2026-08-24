package app.acervatim.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Persistance de la densité d'affichage : elle est **par type de collection**, comme le mode de
 * référencement — revenir sur une collection doit retrouver la vue qu'on y avait laissée.
 */
class DataStoreSettingsRepositoryDisplayModeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tmp.newFile("settings.preferences_pb") }
        )
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `sans choix, chaque type garde sa densite historique`() = runTest {
        // La vue séries a toujours été une grille : introduire DisplayMode ne doit pas la lui retirer.
        assertEquals(DisplayMode.LIST, repository.observeDisplayMode(CollectionType.VINYL).first())
        assertEquals(DisplayMode.GRID, repository.observeDisplayMode(CollectionType.MANGA).first())
    }

    @Test
    fun `le choix est persiste par type, sans deborder sur l'autre`() = runTest {
        repository.setDisplayMode(CollectionType.VINYL, DisplayMode.GALLERY)

        assertEquals(DisplayMode.GALLERY, repository.observeDisplayMode(CollectionType.VINYL).first())
        assertEquals(DisplayMode.GRID, repository.observeDisplayMode(CollectionType.MANGA).first())
    }

    @Test
    fun `une valeur persistee inconnue retombe sur le defaut plutot que de planter`() = runTest {
        repository.setDisplayMode(CollectionType.VINYL, DisplayMode.GALLERY)
        dataStore.putRaw(key = "display_mode_VINYL", value = "MOSAIQUE_3D")

        assertEquals(DisplayMode.LIST, repository.observeDisplayMode(CollectionType.VINYL).first())
    }
}

private suspend fun DataStore<Preferences>.putRaw(key: String, value: String) {
    edit { prefs -> prefs[stringPreferencesKey(key)] = value }
}
