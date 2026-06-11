package fr.beudaert.acervatim.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervatim.domain.FakeRecordRepository
import fr.beudaert.acervatim.domain.model.Artist
import fr.beudaert.acervatim.domain.model.Record
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateRatingUseCaseTest {

    private val now: Instant = Instant.parse("2026-05-16T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `stores valid rating between 1 and 5`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record())
        val useCase = UpdateRatingUseCase(repo, clock)

        useCase("1", 4)

        repo.observeById("1").test {
            assertEquals(4, awaitItem()?.rating)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `zero rating is normalized to null`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(record(rating = 3))
        val useCase = UpdateRatingUseCase(repo, clock)

        useCase("1", 0)

        repo.observeById("1").test {
            assertNull(awaitItem()?.rating)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rejects rating outside 0 to 5`() {
        val repo = FakeRecordRepository()
        val useCase = UpdateRatingUseCase(repo, clock)

        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase("1", 6) }
        }
    }

    private fun record(rating: Int? = null): Record = Record(
        id = "1",
        artists = listOf(Artist("a", "Artist")),
        title = "Title",
        rating = rating,
        createdAt = now,
        updatedAt = now
    )
}
