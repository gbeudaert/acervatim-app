package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.FakeSeriesRepository
import fr.beudaert.acervatim.domain.model.CollectionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpsertSeriesUseCaseTest {

    private val fixed: Instant = Instant.parse("2026-05-19T08:00:00Z")
    private val clock: Clock = Clock.fixed(fixed, ZoneOffset.UTC)

    @Test
    fun `creates a manga series with trimmed fields`() = runTest {
        val repo = FakeSeriesRepository()
        val useCase = UpsertSeriesUseCase(repo, clock)

        val series = useCase(
            SeriesInput(
                title = "  Berserk  ",
                collectionType = CollectionType.MANGA,
                originalAuthor = "  Kentarō Miura ",
                publicationStatus = " En cours ",
                totalVolumes = 42,
                synopsis = "  Guts contre le monde. ",
                coverPath = null
            )
        )

        assertEquals("Berserk", series.title)
        assertEquals("Kentarō Miura", series.originalAuthor)
        assertEquals("En cours", series.publicationStatus)
        assertEquals(42, series.totalVolumes)
        assertEquals("Guts contre le monde.", series.synopsis)
        assertEquals(fixed, series.createdAt)
        assertEquals(fixed, series.updatedAt)
        assertNotNull(series.id)
    }

    @Test
    fun `blank optional fields are normalized to null`() = runTest {
        val repo = FakeSeriesRepository()
        val useCase = UpsertSeriesUseCase(repo, clock)

        val series = useCase(
            SeriesInput(
                title = "Title",
                collectionType = CollectionType.MANGA,
                originalAuthor = "  ",
                publicationStatus = "",
                totalVolumes = null,
                synopsis = "  "
            )
        )

        assertNull(series.originalAuthor)
        assertNull(series.publicationStatus)
        assertNull(series.totalVolumes)
        assertNull(series.synopsis)
    }

    @Test
    fun `blank title throws`() = runTest {
        val repo = FakeSeriesRepository()
        val useCase = UpsertSeriesUseCase(repo, clock)
        try {
            useCase(SeriesInput(title = "  ", collectionType = CollectionType.MANGA))
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("title"))
        }
    }

    @Test
    fun `negative totalVolumes throws`() = runTest {
        val repo = FakeSeriesRepository()
        val useCase = UpsertSeriesUseCase(repo, clock)
        try {
            useCase(
                SeriesInput(
                    title = "X",
                    collectionType = CollectionType.MANGA,
                    totalVolumes = -1
                )
            )
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("totalVolumes"))
        }
    }

    @Test
    fun `update preserves id and createdAt`() = runTest {
        val repo = FakeSeriesRepository()
        val useCase = UpsertSeriesUseCase(repo, clock)
        val initial = useCase(
            SeriesInput(title = "Initial", collectionType = CollectionType.MANGA)
        )

        val createdAt = initial.createdAt
        val laterClock = Clock.fixed(fixed.plusSeconds(3600), ZoneOffset.UTC)
        val updatedUseCase = UpsertSeriesUseCase(repo, laterClock)
        val updated = updatedUseCase(
            SeriesInput(title = "Renamed", collectionType = CollectionType.MANGA),
            existing = initial
        )

        assertEquals(initial.id, updated.id)
        assertEquals(createdAt, updated.createdAt)
        assertNotEquals(createdAt, updated.updatedAt)
        assertEquals("Renamed", updated.title)
    }

    @Test
    fun `duplicate natural key is rejected with DuplicateSeriesException`() = runTest {
        val repo = FakeSeriesRepository()
        val useCase = UpsertSeriesUseCase(repo, clock)
        val first = useCase(
            SeriesInput(
                title = "Berserk",
                collectionType = CollectionType.MANGA,
                originalAuthor = "Kentarō Miura"
            )
        )
        try {
            useCase(
                SeriesInput(
                    title = "  berserk ",                 // casse + espaces différents
                    collectionType = CollectionType.MANGA,
                    originalAuthor = "Kentaro Miura"      // accent retiré
                )
            )
            fail("Expected DuplicateSeriesException")
        } catch (e: DuplicateSeriesException) {
            assertEquals(first.id, e.existingSeriesId)
        }
    }

    @Test
    fun `update of an existing series does not raise duplicate on its own key`() = runTest {
        val repo = FakeSeriesRepository()
        val useCase = UpsertSeriesUseCase(repo, clock)
        val initial = useCase(
            SeriesInput(
                title = "Vagabond",
                collectionType = CollectionType.MANGA,
                originalAuthor = "Takehiko Inoue"
            )
        )
        // Modifier le synopsis sans toucher à la clé naturelle ne doit pas déclencher de doublon.
        val updated = useCase(
            SeriesInput(
                title = "Vagabond",
                collectionType = CollectionType.MANGA,
                originalAuthor = "Takehiko Inoue",
                synopsis = "Musashi en quête de l'invincibilité."
            ),
            existing = initial
        )
        assertEquals(initial.id, updated.id)
        assertEquals("Musashi en quête de l'invincibilité.", updated.synopsis)
    }
}
