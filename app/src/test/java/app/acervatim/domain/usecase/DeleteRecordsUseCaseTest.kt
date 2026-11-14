package app.acervatim.domain.usecase

import app.acervatim.domain.FakeSeriesCatchUpStore
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DeleteRecordsUseCaseTest {

    private val now: Instant = Instant.parse("2026-07-20T09:00:00Z")

    private val records = FakeRecordRepository()
    private val series = FakeSeriesRepository(records)
    private val useCase = DeleteRecordsUseCase(
        deleteRecord = DeleteRecordUseCase(records),
        deleteSeries = DeleteSeriesUseCase(series, records, FakeSeriesCatchUpStore()),
        recordRepository = records
    )

    @Test
    fun `deletes selected entries`() = runTest {
        records.upsert(record("1"))
        records.upsert(record("2"))
        records.upsert(record("3"))

        val result = useCase(recordIds = listOf("1", "2"))

        assertEquals(2, result.deletedRecords)
        assertEquals(2, result.total)
        assertEquals(setOf("3"), records.listAll().map { it.id }.toSet())
    }

    @Test
    fun `cascade deletes the series and all its tomes`() = runTest {
        seedSeriesWithTomes("s1", 3)

        val result = useCase(seriesIds = listOf("s1"), cascade = true)

        assertEquals(1, result.deletedSeries)
        assertEquals(3, result.deletedTomes)
        assertTrue(series.listAll().isEmpty())
        assertTrue(records.listAll().isEmpty())
    }

    @Test
    fun `orphan mode deletes only the series node and keeps the tomes`() = runTest {
        seedSeriesWithTomes("s1", 3)

        val result = useCase(seriesIds = listOf("s1"), cascade = false)

        assertEquals(1, result.deletedSeries)
        assertEquals(0, result.deletedTomes)
        assertTrue(series.listAll().isEmpty())
        // Les tomes subsistent (orphelins).
        assertEquals(3, records.listAll().size)
    }

    @Test
    fun `mixed selection deletes entries and cascaded series`() = runTest {
        records.upsert(record("v1", collectionType = CollectionType.VINYL))
        seedSeriesWithTomes("s1", 2)

        val result = useCase(recordIds = listOf("v1"), seriesIds = listOf("s1"), cascade = true)

        assertEquals(1, result.deletedRecords)
        assertEquals(1, result.deletedSeries)
        assertEquals(2, result.deletedTomes)
        assertEquals(4, result.total)
        assertTrue(records.listAll().isEmpty())
        assertTrue(series.listAll().isEmpty())
    }

    private suspend fun seedSeriesWithTomes(seriesId: String, count: Int) {
        series.upsert(
            Series(
                id = seriesId,
                collectionType = CollectionType.MANGA,
                title = "Série $seriesId",
                createdAt = now,
                updatedAt = now
            )
        )
        repeat(count) { i ->
            records.upsert(
                record(
                    id = "$seriesId-t$i",
                    collectionType = CollectionType.MANGA,
                    seriesId = seriesId,
                    volumeNumber = i + 1
                )
            )
        }
    }

    private fun record(
        id: String,
        collectionType: CollectionType = CollectionType.VINYL,
        seriesId: String? = null,
        volumeNumber: Int? = null
    ): Record = Record(
        id = id,
        collectionType = collectionType,
        seriesId = seriesId,
        volumeNumber = volumeNumber,
        artists = listOf(Artist(id = "", name = "Artiste")),
        title = "Item $id",
        status = ItemStatus.OWNED,
        createdAt = now,
        updatedAt = now
    )
}
