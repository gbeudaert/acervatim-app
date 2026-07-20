package app.acervatim.ui.series

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.usecase.DeleteRecordUseCase
import app.acervatim.domain.usecase.DeleteRecordsUseCase
import app.acervatim.domain.usecase.DeleteSeriesUseCase
import app.acervatim.domain.usecase.ObserveSeriesByIdUseCase
import app.acervatim.domain.usecase.ObserveSeriesItemsUseCase
import app.acervatim.domain.usecase.RefreshMetadataUseCase
import app.acervatim.domain.usecase.UpdateItemStatusUseCase
import app.acervatim.domain.usecase.UpdateRecordUseCase
import app.acervatim.domain.usecase.UpdateRecordsUseCase
import kotlinx.coroutines.Dispatchers
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

class SeriesDetailDeleteViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now: Instant = Instant.parse("2026-06-29T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var records: FakeRecordRepository
    private lateinit var series: FakeSeriesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        records = FakeRecordRepository()
        series = FakeSeriesRepository(records)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(seriesId: String): SeriesDetailViewModel = SeriesDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("seriesId" to seriesId)),
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
            externalSearch = FakeExternalSearchRepository(),
            coverStorage = NoopCoverStorage,
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

    private suspend fun seedSeriesWithTomes() {
        series.upsert(
            Series(
                id = "s1",
                collectionType = CollectionType.MANGA,
                title = "L'attaque des titans",
                createdAt = now,
                updatedAt = now
            )
        )
        repeat(3) { i ->
            records.upsert(
                Record(
                    id = "t$i",
                    collectionType = CollectionType.MANGA,
                    seriesId = "s1",
                    volumeNumber = i + 1,
                    artists = listOf(Artist(id = "", name = "Hajime Isayama")),
                    title = "Tome ${i + 1}",
                    isbn = "isbn-$i",
                    status = ItemStatus.OWNED,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    @Test
    fun `cascade delete removes the series and all its tomes`() = runTest {
        seedSeriesWithTomes()
        val v = vm("s1")

        v.onDelete(cascade = true)
        advanceUntilIdle()

        assertTrue(series.listAll().isEmpty())
        assertTrue(records.listAll().none { it.seriesId == "s1" })
        assertEquals(0, records.listAll().size)
        assertTrue(v.events.value is SeriesDetailEvent.Deleted)
    }

    @Test
    fun `orphan delete removes only the series node and keeps the tomes`() = runTest {
        seedSeriesWithTomes()
        val v = vm("s1")

        v.onDelete(cascade = false)
        advanceUntilIdle()

        assertTrue(series.listAll().isEmpty())
        assertEquals(3, records.listAll().size)
        assertTrue(v.events.value is SeriesDetailEvent.Deleted)
    }
}
