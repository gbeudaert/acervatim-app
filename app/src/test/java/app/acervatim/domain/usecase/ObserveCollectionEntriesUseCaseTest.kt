package app.acervatim.domain.usecase

import app.cash.turbine.test
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSettingsRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveCollectionEntriesUseCaseTest {

    private val now: Instant = Instant.parse("2026-05-17T10:00:00Z")

    @Test
    fun `duplicate ON produces one entry per (record, artist)`() = runTest {
        val records = FakeRecordRepository()
        val settings = FakeSettingsRepository(initialDuplicate = true)
        records.upsert(
            Record(
                id = "1",
                artists = listOf(Artist("dp", "Daft Punk"), Artist("pw", "Pharrell")),
                title = "Get Lucky",
                createdAt = now,
                updatedAt = now
            )
        )

        val useCase = ObserveCollectionEntriesUseCase(records, settings)
        useCase(RecordFilter()).test {
            val entries = awaitItem()
            assertEquals(2, entries.size)
            assertEquals(listOf("Daft Punk", "Pharrell"), entries.map { it.displayedArtist.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate OFF produces one entry per record under primary artist`() = runTest {
        val records = FakeRecordRepository()
        val settings = FakeSettingsRepository(initialDuplicate = false)
        records.upsert(
            Record(
                id = "1",
                artists = listOf(Artist("dp", "Daft Punk"), Artist("pw", "Pharrell")),
                title = "Get Lucky",
                createdAt = now,
                updatedAt = now
            )
        )

        val useCase = ObserveCollectionEntriesUseCase(records, settings)
        useCase(RecordFilter()).test {
            val entries = awaitItem()
            assertEquals(1, entries.size)
            val entry = entries.single()
            assertEquals("Daft Punk", entry.displayedArtist.name)
            assertEquals(listOf("Pharrell"), entry.featuringArtists.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flipping the flag swaps the emission shape`() = runTest {
        val records = FakeRecordRepository()
        val settings = FakeSettingsRepository(initialDuplicate = true)
        records.upsert(
            Record(
                id = "1",
                artists = listOf(Artist("a", "A"), Artist("b", "B")),
                title = "T",
                createdAt = now,
                updatedAt = now
            )
        )

        val useCase = ObserveCollectionEntriesUseCase(records, settings)
        useCase(RecordFilter()).test {
            assertEquals(2, awaitItem().size)
            settings.setDuplicateAlbumsPerArtist(false)
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
