package fr.beudaert.acervus.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervus.domain.FakeRecordRepository
import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ToggleWishlistUseCaseTest {

    private val now: Instant = Instant.parse("2026-05-16T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `flips OWNED to WISHLIST and updates timestamp`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record(status = RecordStatus.OWNED))
        val useCase = ToggleWishlistUseCase(repo, clock)

        repo.observeById("1").test {
            assertEquals(RecordStatus.OWNED, awaitItem()?.status)
            useCase(id = "1", wishlist = true)
            val updated = awaitItem()
            assertEquals(RecordStatus.WISHLIST, updated?.status)
            assertEquals(now, updated?.updatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flips WISHLIST back to OWNED`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record(status = RecordStatus.WISHLIST))
        val useCase = ToggleWishlistUseCase(repo, clock)

        useCase(id = "1", wishlist = false)

        repo.observeById("1").test {
            assertEquals(RecordStatus.OWNED, awaitItem()?.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun record(status: RecordStatus): Record = Record(
        id = "1",
        artists = listOf(Artist("a", "Artist")),
        title = "Title",
        status = status,
        createdAt = now,
        updatedAt = now
    )
}
