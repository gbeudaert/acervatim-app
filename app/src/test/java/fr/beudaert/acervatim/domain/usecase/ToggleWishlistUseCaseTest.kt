package fr.beudaert.acervatim.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervatim.domain.FakeRecordRepository
import fr.beudaert.acervatim.domain.model.Artist
import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.ItemStatus
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
        repo.upsert(record(status = ItemStatus.OWNED))
        val useCase = ToggleWishlistUseCase(repo, clock)

        repo.observeById("1").test {
            assertEquals(ItemStatus.OWNED, awaitItem()?.status)
            useCase(id = "1", wishlist = true)
            val updated = awaitItem()
            assertEquals(ItemStatus.WISHLIST, updated?.status)
            assertEquals(now, updated?.updatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flips WISHLIST back to OWNED`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record(status = ItemStatus.WISHLIST))
        val useCase = ToggleWishlistUseCase(repo, clock)

        useCase(id = "1", wishlist = false)

        repo.observeById("1").test {
            assertEquals(ItemStatus.OWNED, awaitItem()?.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun record(status: ItemStatus): Record = Record(
        id = "1",
        artists = listOf(Artist("a", "Artist")),
        title = "Title",
        status = status,
        createdAt = now,
        updatedAt = now
    )
}
