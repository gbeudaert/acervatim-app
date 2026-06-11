package fr.beudaert.acervatim.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervatim.domain.FakeRecordRepository
import fr.beudaert.acervatim.domain.model.Artist
import fr.beudaert.acervatim.domain.model.Record
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveRecordsUseCaseTest {

    @Test
    fun `emits empty list initially then updates as records are added`() = runTest {
        val repo = FakeRecordRepository()
        val useCase = ObserveRecordsUseCase(repo)
        val now = Instant.parse("2026-05-16T10:00:00Z")

        useCase().test {
            assertEquals(emptyList<Record>(), awaitItem())

            repo.upsert(
                Record(
                    id = "1",
                    artists = listOf(Artist("a", "A")),
                    title = "B",
                    createdAt = now,
                    updatedAt = now
                )
            )

            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("1", emitted.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
