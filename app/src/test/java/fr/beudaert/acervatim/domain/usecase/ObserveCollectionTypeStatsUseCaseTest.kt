package fr.beudaert.acervatim.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervatim.domain.FakeRecordRepository
import fr.beudaert.acervatim.domain.FakeSeriesRepository
import fr.beudaert.acervatim.domain.model.Artist
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.ItemStatus
import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.Series
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveCollectionTypeStatsUseCaseTest {

    private val now: Instant = Instant.parse("2026-05-19T10:00:00Z")

    @Test
    fun `aggregates record count for VINYL and series count for MANGA`() = runTest {
        val recordRepo = FakeRecordRepository()
        val seriesRepo = FakeSeriesRepository(recordRepo)
        val useCase = ObserveCollectionTypeStatsUseCase(recordRepo, seriesRepo)

        // 2 vinyles + 3 mangas (records) + 2 séries
        recordRepo.upsert(record(id = "v1", type = CollectionType.VINYL))
        recordRepo.upsert(record(id = "v2", type = CollectionType.VINYL))
        recordRepo.upsert(record(id = "m1", type = CollectionType.MANGA, seriesId = "s1"))
        recordRepo.upsert(record(id = "m2", type = CollectionType.MANGA, seriesId = "s1"))
        recordRepo.upsert(record(id = "m3", type = CollectionType.MANGA, seriesId = "s2"))
        seriesRepo.upsert(series(id = "s1"))
        seriesRepo.upsert(series(id = "s2"))

        useCase().test {
            val stats = awaitItem()
            val vinyl = stats.first { it.collectionType == CollectionType.VINYL }
            val manga = stats.first { it.collectionType == CollectionType.MANGA }
            assertEquals(2, vinyl.recordCount)
            // displayCount = recordCount pour VINYL (FLAT)
            assertEquals(2, vinyl.displayCount)
            assertEquals(3, manga.recordCount)
            assertEquals(2, manga.seriesCount)
            // displayCount = seriesCount pour MANGA (SERIAL)
            assertEquals(2, manga.displayCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun record(
        id: String,
        type: CollectionType,
        seriesId: String? = null,
        status: ItemStatus = ItemStatus.OWNED
    ): Record = Record(
        id = id,
        collectionType = type,
        seriesId = seriesId,
        artists = listOf(Artist(id = "a-$id", name = "Artist")),
        title = "Title $id",
        status = status,
        createdAt = now,
        updatedAt = now
    )

    private fun series(id: String): Series = Series(
        id = id,
        collectionType = CollectionType.MANGA,
        title = "Series $id",
        createdAt = now,
        updatedAt = now
    )
}
