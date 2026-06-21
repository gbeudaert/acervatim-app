package app.acervatim.domain.usecase

import app.cash.turbine.test
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Couvre le filtre texte de la vue SERIAL (Sprint 9) : recherche par titre ou auteur,
 * insensible à la casse et aux accents, en plus du filtre par type de collection.
 */
class ObserveSeriesWithCountsUseCaseTest {

    private val now: Instant = Instant.parse("2026-05-19T08:00:00Z")

    @Test
    fun `empty query returns all series of the collection type`() = runTest {
        val repo = seededRepo()
        val useCase = ObserveSeriesWithCountsUseCase(repo)

        useCase(CollectionType.MANGA).test {
            val titles = awaitItem().map { it.series.title }.toSet()
            assertEquals(setOf("Berserk", "Naruto", "Émma"), titles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query filters on title without crossing collection types`() = runTest {
        val repo = seededRepo()
        val useCase = ObserveSeriesWithCountsUseCase(repo)

        // "Berserk" (MANGA) matche, mais "Berserk Soundtrack" (VINYL) ne doit pas remonter.
        useCase(CollectionType.MANGA, "ber").test {
            assertEquals(listOf("Berserk"), awaitItem().map { it.series.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query filters on author`() = runTest {
        val repo = seededRepo()
        val useCase = ObserveSeriesWithCountsUseCase(repo)

        useCase(CollectionType.MANGA, "miura").test {
            assertEquals(listOf("Berserk"), awaitItem().map { it.series.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query is case and accent insensitive`() = runTest {
        val repo = seededRepo()
        val useCase = ObserveSeriesWithCountsUseCase(repo)

        useCase(CollectionType.MANGA, "EMMA").test {
            assertEquals(listOf("Émma"), awaitItem().map { it.series.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `query stays within the requested collection type`() = runTest {
        val repo = seededRepo()
        val useCase = ObserveSeriesWithCountsUseCase(repo)

        // Côté VINYL, "ber" ne doit remonter que la série vinyle, pas le manga homonyme.
        useCase(CollectionType.VINYL, "ber").test {
            assertEquals(listOf("Berserk Soundtrack"), awaitItem().map { it.series.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun seededRepo(): FakeSeriesRepository {
        val repo = FakeSeriesRepository()
        repo.upsert(series(id = "m1", title = "Berserk", author = "Kentarō Miura"))
        repo.upsert(series(id = "m2", title = "Naruto", author = "Masashi Kishimoto"))
        repo.upsert(series(id = "m3", title = "Émma", author = "Kaoru Mori"))
        repo.upsert(
            series(id = "v1", title = "Berserk Soundtrack", author = null, type = CollectionType.VINYL)
        )
        return repo
    }

    private fun series(
        id: String,
        title: String,
        author: String?,
        type: CollectionType = CollectionType.MANGA
    ): Series = Series(
        id = id,
        collectionType = type,
        title = title,
        originalAuthor = author,
        createdAt = now,
        updatedAt = now
    )
}
