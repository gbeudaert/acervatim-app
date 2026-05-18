package fr.beudaert.acervus.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervus.domain.FakeRecordRepository
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.model.RecordStatus
import fr.beudaert.acervus.domain.model.StatusFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveFilteredRecordsUseCaseTest {

    private val now: Instant = Instant.parse("2026-05-16T10:00:00Z")

    @Test
    fun `text query filters on artist and title case-insensitively`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record(id = "1", artistName = "Pink Floyd", title = "Animals"))
        repo.upsert(record(id = "2", artistName = "Radiohead", title = "OK Computer"))
        val useCase = ObserveFilteredRecordsUseCase(repo)

        useCase(RecordFilter(query = "pink")).test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("1", emitted.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status filter narrows to wishlist only`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record(id = "1", artistName = "A", status = RecordStatus.OWNED))
        repo.upsert(record(id = "2", artistName = "B", status = RecordStatus.WISHLIST))
        val useCase = ObserveFilteredRecordsUseCase(repo)

        useCase(RecordFilter(status = StatusFilter.WISHLIST_ONLY)).test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals(RecordStatus.WISHLIST, emitted.first().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `format filter narrows results`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record(id = "1", artistName = "A", format = AcervusFormat.LP_33))
        repo.upsert(record(id = "2", artistName = "B", format = AcervusFormat.SINGLE_45))
        val useCase = ObserveFilteredRecordsUseCase(repo)

        useCase(RecordFilter(format = AcervusFormat.SINGLE_45)).test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("2", emitted.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun record(
        id: String,
        artistName: String,
        title: String = "Title",
        status: RecordStatus = RecordStatus.OWNED,
        format: AcervusFormat? = null
    ): Record = Record(
        id = id,
        artists = listOf(Artist(id = "a-$id", name = artistName)),
        title = title,
        status = status,
        format = format,
        createdAt = now,
        updatedAt = now
    )
}
