package app.acervatim.domain.usecase

import app.cash.turbine.test
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.Record
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LastPlayedUseCasesTest {

    private val now: Instant = Instant.parse("2026-05-16T10:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `mark as played stamps current clock instant`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(seedRecord())
        val useCase = MarkAsPlayedUseCase(repo, clock)

        useCase("1")

        repo.observeById("1").test {
            assertEquals(now, awaitItem()?.lastPlayedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset last played sets it back to null`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(seedRecord(lastPlayedAt = now))
        val resetUseCase = ResetLastPlayedUseCase(repo)

        repo.observeById("1").test {
            assertNotNull(awaitItem()?.lastPlayedAt)
            resetUseCase("1")
            assertNull(awaitItem()?.lastPlayedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mark as played increments the counter alongside the timestamp`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(seedRecord())
        val useCase = MarkAsPlayedUseCase(repo, clock)

        useCase("1")
        useCase("1")
        useCase("1")

        repo.observeById("1").test {
            val record = awaitItem()!!
            assertEquals(3, record.playCount)
            assertEquals(now, record.lastPlayedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset clears the counter as well as the date`() = runTest {
        // Garder un compteur derrière une dernière écoute effacée laisserait un état incohérent.
        val repo = FakeRecordRepository()
        repo.upsert(seedRecord(lastPlayedAt = now, playCount = 5))
        val resetUseCase = ResetLastPlayedUseCase(repo)

        resetUseCase("1")

        repo.observeById("1").test {
            val record = awaitItem()!!
            assertNull(record.lastPlayedAt)
            assertEquals(0, record.playCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun seedRecord(lastPlayedAt: Instant? = null, playCount: Int = 0): Record = Record(
        id = "1",
        artists = listOf(Artist("a", "Artist")),
        title = "Title",
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        createdAt = now,
        updatedAt = now
    )
}
