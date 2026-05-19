package fr.beudaert.acervus.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervus.domain.FakeRecordRepository
import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.Record
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveSeriesItemsUseCaseTest {

    private val now: Instant = Instant.parse("2026-05-19T08:00:00Z")

    @Test
    fun `returns only items of the given series, sorted by volume`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(volume(id = "v3", seriesId = "s1", volume = 3))
        repo.upsert(volume(id = "v1", seriesId = "s1", volume = 1))
        repo.upsert(volume(id = "v2", seriesId = "s1", volume = 2))
        repo.upsert(volume(id = "vX", seriesId = "s2", volume = 1)) // autre série
        repo.upsert(volume(id = "vNoSeries", seriesId = null, volume = null))

        val useCase = ObserveSeriesItemsUseCase(repo)

        useCase("s1").test {
            val items = awaitItem()
            assertEquals(listOf("v1", "v2", "v3"), items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `items without volume number sink to the end`() = runTest {
        val repo = FakeRecordRepository()
        repo.upsert(volume(id = "noNum", seriesId = "s1", volume = null))
        repo.upsert(volume(id = "v1", seriesId = "s1", volume = 1))

        val useCase = ObserveSeriesItemsUseCase(repo)

        useCase("s1").test {
            val items = awaitItem()
            assertEquals(listOf("v1", "noNum"), items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun volume(
        id: String,
        seriesId: String?,
        volume: Int?,
        status: ItemStatus = ItemStatus.OWNED
    ): Record = Record(
        id = id,
        collectionType = CollectionType.VINYL,
        seriesId = seriesId,
        volumeNumber = volume,
        artists = listOf(Artist(id = "a-$id", name = "Artist")),
        title = "Title $id",
        status = status,
        createdAt = now,
        updatedAt = now
    )
}
