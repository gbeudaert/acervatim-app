package app.acervatim.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.acervatim.domain.model.CollectionType
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
 * Test pur JVM (sans Robolectric) : DataStore n'a besoin que d'un fichier, on lui en fournit un
 * temporaire. Valide la sémantique délicate de [DataStoreSettingsRepository.observeSyncedCollectionTypes]
 * (clé absente = tout synchronisé ; ensemble vide autorisé via marqueur).
 */
class DataStoreSettingsRepositorySyncedTest {

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
    fun `default is all types synced`() = runTest {
        assertEquals(CollectionType.entries.toSet(), repository.observeSyncedCollectionTypes().first())
    }

    @Test
    fun `disabling a type removes only that type`() = runTest {
        repository.setCollectionTypeSynced(CollectionType.MANGA, false)
        assertEquals(setOf(CollectionType.VINYL), repository.observeSyncedCollectionTypes().first())
    }

    @Test
    fun `empty set is allowed and persisted (all local)`() = runTest {
        CollectionType.entries.forEach { repository.setCollectionTypeSynced(it, false) }
        assertEquals(emptySet<CollectionType>(), repository.observeSyncedCollectionTypes().first())
    }

    @Test
    fun `re-enabling from empty adds back the type`() = runTest {
        CollectionType.entries.forEach { repository.setCollectionTypeSynced(it, false) }
        repository.setCollectionTypeSynced(CollectionType.VINYL, true)
        assertEquals(setOf(CollectionType.VINYL), repository.observeSyncedCollectionTypes().first())
    }
}
