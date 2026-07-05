package app.acervatim.ui.settings

import app.cash.turbine.test
import app.acervatim.domain.FakeOAuthRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.FakeSettingsRepository
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.usecase.ConnectSourceUseCase
import app.acervatim.domain.usecase.DisconnectSourceUseCase
import app.acervatim.domain.usecase.ObserveCollectionTypeStatsUseCase
import app.acervatim.domain.usecase.ObserveConnectedProvidersUseCase
import app.acervatim.domain.usecase.ObserveListModeForCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveSyncedCollectionTypesUseCase
import app.acervatim.domain.usecase.ObserveVisibleCollectionTypesUseCase
import app.acervatim.domain.usecase.RefreshConnectedProvidersUseCase
import app.acervatim.domain.usecase.SetCollectionTypeSyncedUseCase
import app.acervatim.domain.usecase.SetCollectionTypeVisibleUseCase
import app.acervatim.domain.usecase.SetListModeForCollectionTypeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ManageCollectionTypesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var settings: FakeSettingsRepository
    private lateinit var oauth: FakeOAuthRepository
    private lateinit var recordRepo: FakeRecordRepository
    private lateinit var seriesRepo: FakeSeriesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = FakeSettingsRepository()
        oauth = FakeOAuthRepository(initialConnected = setOf("discogs"))
        recordRepo = FakeRecordRepository()
        seriesRepo = FakeSeriesRepository(recordRepo)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ManageCollectionTypesViewModel(
        observeVisible = ObserveVisibleCollectionTypesUseCase(settings),
        observeSynced = ObserveSyncedCollectionTypesUseCase(settings),
        observeStats = ObserveCollectionTypeStatsUseCase(recordRepo, seriesRepo),
        observeListMode = ObserveListModeForCollectionTypeUseCase(settings),
        observeConnected = ObserveConnectedProvidersUseCase(oauth),
        setVisible = SetCollectionTypeVisibleUseCase(settings),
        setSynced = SetCollectionTypeSyncedUseCase(settings),
        setListMode = SetListModeForCollectionTypeUseCase(settings),
        connectSource = ConnectSourceUseCase(oauth),
        disconnectSource = DisconnectSourceUseCase(oauth),
        refreshConnected = RefreshConnectedProvidersUseCase(oauth)
    )

    @Test
    fun `init refreshes connected providers`() = runTest {
        vm()
        advanceUntilIdle()
        assertTrue(oauth.refreshCount >= 1)
    }

    @Test
    fun `default state exposes visibility sync and token aggregate per type`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            skipItems(1) // valeur initiale (avant collecte des flux)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            val vinyl = state.first { it.type == CollectionType.VINYL }
            val manga = state.first { it.type == CollectionType.MANGA }

            // Défauts : tout visible, tout synchronisé.
            assertTrue(vinyl.visible)
            assertTrue(vinyl.synced)
            assertTrue(manga.synced)

            // Discogs connecté ⇒ 1/1 pour le vinyle.
            assertEquals(1, vinyl.tokenSources.size)
            assertEquals(1, vinyl.connectedTokenCount)

            // Manga : MAL (jeton) non connecté + BnF publique ⇒ 1 source à jeton, 0 connectée.
            assertTrue(manga.hasTokenSources)
            assertEquals(1, manga.tokenSources.size)
            assertEquals(0, manga.connectedTokenCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling synced off is reflected in state`() = runTest {
        val viewModel = vm()
        viewModel.uiState.test {
            advanceUntilIdle()
            viewModel.onSyncedChange(CollectionType.VINYL, false)
            advanceUntilIdle()
            val vinyl = expectMostRecentItem().first { it.type == CollectionType.VINYL }
            assertFalse(vinyl.synced)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `last visible type cannot be hidden`() = runTest {
        settings = FakeSettingsRepository(initialVisibleTypes = setOf(CollectionType.VINYL))
        val viewModel = vm()
        viewModel.uiState.test {
            advanceUntilIdle()
            val vinyl = expectMostRecentItem().first { it.type == CollectionType.VINYL }
            assertTrue(vinyl.visible)
            assertFalse(vinyl.visibilityToggleEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connecting a source emits open url event`() = runTest {
        val viewModel = vm()
        viewModel.events.test {
            viewModel.onConnectSource("mal")
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is SourcesEvent.OpenUrl)
            assertEquals("https://auth.example/authorize", (event as SourcesEvent.OpenUrl).url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed connection emits error event`() = runTest {
        oauth = FakeOAuthRepository(connectFails = true)
        val viewModel = vm()
        viewModel.events.test {
            viewModel.onConnectSource("mal")
            advanceUntilIdle()
            assertEquals(SourcesEvent.ConnectError, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
