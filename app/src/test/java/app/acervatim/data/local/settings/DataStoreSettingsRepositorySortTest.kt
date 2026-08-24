package app.acervatim.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.RecordSortField
import app.acervatim.domain.model.SortDirection
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
 * Persistance du tri : il survit à la fermeture de l'app et reste **par type de collection**,
 * comme le mode de référencement et la densité. Trier ses vinyles par année n'engage pas ses mangas.
 */
class DataStoreSettingsRepositorySortTest {

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
    fun `sans choix, l'ordre reste celui d'avant le tri choisissable`() = runTest {
        assertEquals(RecordSort.DEFAULT, repository.observeSort(CollectionType.VINYL).first())
    }

    @Test
    fun `le choix est persiste par type, sans deborder sur l'autre`() = runTest {
        val byYear = RecordSort(RecordSortField.YEAR, SortDirection.DESCENDING)
        repository.setSort(CollectionType.VINYL, byYear)

        assertEquals(byYear, repository.observeSort(CollectionType.VINYL).first())
        assertEquals(RecordSort.DEFAULT, repository.observeSort(CollectionType.MANGA).first())
    }

    @Test
    fun `champ et sens font l'aller-retour, sens compris`() = runTest {
        val ascendingRating = RecordSort(RecordSortField.RATING, SortDirection.ASCENDING)
        repository.setSort(CollectionType.VINYL, ascendingRating)

        assertEquals(ascendingRating, repository.observeSort(CollectionType.VINYL).first())
    }

    @Test
    fun `une valeur illisible retombe sur le defaut plutot que de planter`() = runTest {
        repository.setSort(CollectionType.VINYL, RecordSort(RecordSortField.YEAR))

        // Champ disparu d'une version à l'autre, puis format inattendu.
        dataStore.putRaw("sort_VINYL", "DUREE:ASCENDING")
        assertEquals(RecordSort.DEFAULT, repository.observeSort(CollectionType.VINYL).first())

        dataStore.putRaw("sort_VINYL", "YEAR")
        assertEquals(RecordSort.DEFAULT, repository.observeSort(CollectionType.VINYL).first())
    }
}

private suspend fun DataStore<Preferences>.putRaw(key: String, value: String) {
    edit { prefs -> prefs[stringPreferencesKey(key)] = value }
}
