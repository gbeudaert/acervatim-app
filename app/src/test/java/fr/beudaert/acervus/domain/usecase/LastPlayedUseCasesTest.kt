package fr.beudaert.acervus.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervus.domain.FakeRecordRepository
import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.Record
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

    private fun seedRecord(lastPlayedAt: Instant? = null): Record = Record(
        id = "1",
        artists = listOf(Artist("a", "Artist")),
        title = "Title",
        lastPlayedAt = lastPlayedAt,
        createdAt = now,
        updatedAt = now
    )
}
