package app.acervatim.ui.series

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.FakeAuthRepository
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesCatchUpStore
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesCatchUpState
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.usecase.DeleteRecordUseCase
import app.acervatim.domain.usecase.DeleteRecordsUseCase
import app.acervatim.domain.usecase.DeleteSeriesUseCase
import app.acervatim.domain.usecase.ImportScannedSeriesUseCase
import app.acervatim.domain.usecase.ObserveSeriesByIdUseCase
import app.acervatim.domain.usecase.ObserveSeriesItemsUseCase
import app.acervatim.domain.usecase.RefreshMetadataUseCase
import app.acervatim.domain.usecase.SeriesCatchUpUseCase
import app.acervatim.domain.usecase.SeriesUpdatesCache
import app.acervatim.domain.usecase.UpdateItemStatusUseCase
import app.acervatim.domain.usecase.UpdateRecordUseCase
import app.acervatim.domain.usecase.UpdateRecordsUseCase
import app.acervatim.ui.sharing.FakeAccountRepository
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Bannière « nouveaux tomes » : un échec d'ajout ne doit pas figer le bouton. */
class SeriesDetailNewVolumesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var records: FakeRecordRepository
    private lateinit var series: FakeSeriesRepository
    private lateinit var store: FakeSeriesCatchUpStore
    private lateinit var search: FakeExternalSearchRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        records = FakeRecordRepository()
        series = FakeSeriesRepository(records)
        search = FakeExternalSearchRepository()
        store = FakeSeriesCatchUpStore().apply { currentOwner = "u1" }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): SeriesDetailViewModel = SeriesDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("seriesId" to "s1")),
        observeSeries = ObserveSeriesByIdUseCase(series),
        observeItems = ObserveSeriesItemsUseCase(records),
        updateItemStatus = UpdateItemStatusUseCase(records, clock),
        deleteSeries = DeleteSeriesUseCase(series, records, store),
        updateRecords = UpdateRecordsUseCase(records, UpdateRecordUseCase(records, clock)),
        deleteRecords = DeleteRecordsUseCase(
            deleteRecord = DeleteRecordUseCase(records),
            deleteSeries = DeleteSeriesUseCase(series, records, store),
            recordRepository = records
        ),
        refreshMetadata = RefreshMetadataUseCase(
            recordRepository = records,
            seriesRepository = series,
            externalSearch = search,
            coverStorage = NoopCoverStorage,
            clock = clock
        ),
        catchUp = SeriesCatchUpUseCase(
            seriesRepository = series,
            recordRepository = records,
            externalSearch = search,
            store = store,
            authRepository = FakeAuthRepository(initialUserId = "u1"),
            accountRepository = FakeAccountRepository(),
            importSeries = ImportScannedSeriesUseCase(series, records, search, NoopCoverStorage, clock),
            updatesCache = SeriesUpdatesCache(search, clock),
            clock = clock
        )
    )

    private object NoopCoverStorage : CoverStorage {
        override suspend fun saveFromUri(recordId: String, uriString: String): String = ""
        override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = ""
        override suspend fun saveFromUrl(recordId: String, url: String): String = ""
        override suspend fun delete(recordId: String) = Unit
        override fun pathFor(recordId: String): String = ""
    }

    /** Série suivie, vérifiée aujourd'hui (aucun appel réseau), un tome 15 en attente. */
    private suspend fun seedPending() {
        series.upsert(
            Series(
                id = "s1",
                collectionType = CollectionType.MANGA,
                title = "Frieren",
                originalAuthor = "Kanehito Yamada",
                publicationStatus = "En cours",
                publisherFr = "Ki-oon",
                createdAt = now,
                updatedAt = now
            )
        )
        store.states["s1"] = SeriesCatchUpState(
            lastCheckedOn = LocalDate.of(2026, 11, 2),
            pending = listOf(EditionTome(editionVolume = 15, isbn = "9791032700015", titleFr = "Frieren. 15"))
        )
    }

    @Test
    fun `un echec d'ajout est signale et relache le bouton, la banniere reste`() = runTest {
        seedPending()
        val v = vm()
        advanceUntilIdle()
        assertEquals(1, v.newVolumes.value.size)

        store.failOnWrite = IllegalStateException("DataStore indisponible")
        v.onAddNewVolumes()
        advanceUntilIdle()

        assertTrue(v.newVolumesFailed.value)
        assertFalse(v.addingNewVolumes.value)
        // La bannière reste : l'utilisateur peut réessayer.
        assertEquals(1, v.newVolumes.value.size)

        v.consumeNewVolumesFailed()
        assertFalse(v.newVolumesFailed.value)
    }

    @Test
    fun `un ajout reussi vide la banniere et publie le bilan`() = runTest {
        seedPending()
        val v = vm()
        advanceUntilIdle()

        v.onAddNewVolumes()
        advanceUntilIdle()

        assertFalse(v.addingNewVolumes.value)
        assertTrue(v.newVolumes.value.isEmpty())
        assertEquals(1, v.newVolumesResult.value?.createdVolumes)
    }
}
