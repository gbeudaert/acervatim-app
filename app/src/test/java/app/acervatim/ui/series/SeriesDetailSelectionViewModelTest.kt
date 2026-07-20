package app.acervatim.ui.series

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.usecase.DeleteRecordUseCase
import app.acervatim.domain.usecase.DeleteRecordsUseCase
import app.acervatim.domain.usecase.DeleteSeriesUseCase
import app.acervatim.domain.usecase.FieldEdit
import app.acervatim.domain.usecase.BulkEditDelta
import app.acervatim.domain.usecase.ObserveSeriesByIdUseCase
import app.acervatim.domain.usecase.ObserveSeriesItemsUseCase
import app.acervatim.domain.usecase.RefreshMetadataUseCase
import app.acervatim.domain.usecase.UpdateItemStatusUseCase
import app.acervatim.domain.usecase.UpdateRecordUseCase
import app.acervatim.domain.usecase.UpdateRecordsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SeriesDetailSelectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now: Instant = Instant.parse("2026-07-20T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var records: FakeRecordRepository
    private lateinit var series: FakeSeriesRepository
    private lateinit var externalSearch: FakeExternalSearchRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        records = FakeRecordRepository()
        series = FakeSeriesRepository(records)
        externalSearch = FakeExternalSearchRepository()
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
        deleteSeries = DeleteSeriesUseCase(series, records),
        updateRecords = UpdateRecordsUseCase(records, UpdateRecordUseCase(records, clock)),
        deleteRecords = DeleteRecordsUseCase(
            deleteRecord = DeleteRecordUseCase(records),
            deleteSeries = DeleteSeriesUseCase(series, records),
            recordRepository = records
        ),
        refreshMetadata = RefreshMetadataUseCase(
            recordRepository = records,
            seriesRepository = series,
            externalSearch = externalSearch,
            coverStorage = NoopCoverStorage,
            clock = clock
        )
    )

    private suspend fun seedTomes(count: Int, status: ItemStatus = ItemStatus.WISHLIST) {
        series.upsert(
            Series(id = "s1", collectionType = CollectionType.MANGA, title = "Série", createdAt = now, updatedAt = now)
        )
        repeat(count) { i ->
            records.upsert(
                Record(
                    id = "t$i",
                    collectionType = CollectionType.MANGA,
                    seriesId = "s1",
                    volumeNumber = i + 1,
                    artists = listOf(Artist(id = "", name = "Auteur")),
                    title = "Tome ${i + 1}",
                    isbn = "isbn-$i",
                    status = status,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    @Test
    fun `enter, toggle and clear selection`() = runTest {
        val v = vm()

        v.enterSelection("t0")
        assertEquals(setOf("t0"), v.selectedIds.value)

        v.toggleSelection("t1")
        assertEquals(setOf("t0", "t1"), v.selectedIds.value)

        v.toggleSelection("t0")
        assertEquals(setOf("t1"), v.selectedIds.value)

        v.clearSelection()
        assertTrue(v.selectedIds.value.isEmpty())
    }

    @Test
    fun `select all visible selects every tome`() = runTest {
        seedTomes(3)
        val v = vm()
        // uiState est un stateIn WhileSubscribed : un collecteur doit être actif pour qu'il se peuple.
        backgroundScope.launch { v.uiState.collect {} }
        advanceUntilIdle()

        v.selectAllVisible()

        assertEquals(setOf("t0", "t1", "t2"), v.selectedIds.value)
    }

    @Test
    fun `bulk edit flips selected tomes to owned and clears the selection`() = runTest {
        seedTomes(3, status = ItemStatus.WISHLIST)
        val v = vm()
        v.enterSelection("t0")
        v.toggleSelection("t1")

        v.applyBulkEdit(BulkEditDelta(status = FieldEdit.Set(ItemStatus.OWNED)))
        advanceUntilIdle()

        val all = records.listAll().associateBy { it.id }
        assertEquals(ItemStatus.OWNED, all.getValue("t0").status)
        assertEquals(ItemStatus.OWNED, all.getValue("t1").status)
        // Tome non sélectionné : inchangé.
        assertEquals(ItemStatus.WISHLIST, all.getValue("t2").status)
        assertEquals(2, v.bulkEditResult.value?.updatedCount)
        assertTrue(v.selectedIds.value.isEmpty())
    }

    @Test
    fun `bulk delete removes selected tomes`() = runTest {
        seedTomes(3)
        val v = vm()
        v.enterSelection("t0")
        v.toggleSelection("t2")

        v.applyBulkDelete()
        advanceUntilIdle()

        assertEquals(setOf("t1"), records.listAll().map { it.id }.toSet())
        assertEquals(2, v.bulkDeleteResult.value?.deletedRecords)
        assertTrue(v.selectedIds.value.isEmpty())
    }

    @Test
    fun `refresh fills a missing cover on the selected tome`() = runTest {
        // Tome avec description déjà présente → seule la jaquette est résolue (via /cover).
        series.upsert(
            Series(id = "s1", collectionType = CollectionType.MANGA, title = "Série", createdAt = now, updatedAt = now)
        )
        records.upsert(
            Record(
                id = "t0",
                collectionType = CollectionType.MANGA,
                seriesId = "s1",
                volumeNumber = 1,
                artists = listOf(Artist(id = "", name = "Auteur")),
                title = "Tome 1",
                isbn = "111",
                extras = mapOf(MangaExtras.KEY_TOME_SYNOPSIS to "déjà là"),
                status = ItemStatus.OWNED,
                createdAt = now,
                updatedAt = now
            )
        )
        externalSearch.coverByIsbn = mapOf("111" to "https://cdn.example/t1.jpg")
        val v = vm()
        v.enterSelection("t0")

        v.applyRefresh(overwrite = false)
        advanceUntilIdle()

        assertEquals("https://cdn.example/t1.jpg", records.findByIsbn("111")!!.coverRemoteUrl)
        assertEquals(1, v.refreshResult.value?.refreshedCount)
        assertTrue(v.selectedIds.value.isEmpty())
    }

    private object NoopCoverStorage : CoverStorage {
        override suspend fun saveFromUri(recordId: String, uriString: String): String = "/covers/$recordId"
        override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = "/covers/$recordId"
        override suspend fun saveFromUrl(recordId: String, url: String): String = "/covers/$recordId"
        override suspend fun delete(recordId: String) = Unit
        override fun pathFor(recordId: String): String = "/covers/$recordId"
    }
}
