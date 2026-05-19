package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.FakeRecordRepository
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.RecordingSpeed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateRecordUseCaseTest {

    private val createdAt: Instant = Instant.parse("2026-01-01T12:00:00Z")
    private val updatedInstant: Instant = Instant.parse("2026-05-17T18:00:00Z")
    private val clock: Clock = Clock.fixed(updatedInstant, ZoneOffset.UTC)

    private fun seedRecord(): Record = Record(
        id = "rec-1",
        artists = listOf(Artist("orig", "Original Artist")),
        title = "Original Title",
        year = 2000,
        format = AcervusFormat.LP_33,
        recordingSpeed = RecordingSpeed.RPM_33,
        rating = 3,
        coverPath = "/path/to/old.jpg",
        status = ItemStatus.OWNED,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun `updates fields and preserves createdAt`() = runTest {
        val repo = FakeRecordRepository().apply { upsert(seedRecord()) }
        val useCase = UpdateRecordUseCase(repo, clock)

        val result = useCase(
            existing = seedRecord(),
            input = RecordInput(
                artists = listOf("  New Artist  ", "Featuring"),
                title = "  New Title  ",
                year = 2024,
                format = AcervusFormat.SINGLE_45,
                recordingSpeed = RecordingSpeed.RPM_45,
                rating = 5,
                coverPath = "/path/to/new.jpg",
                status = ItemStatus.WISHLIST
            )
        )

        assertEquals("rec-1", result.id)
        assertEquals(listOf("New Artist", "Featuring"), result.artists.map { it.name })
        assertEquals("New Title", result.title)
        assertEquals(2024, result.year)
        assertEquals(AcervusFormat.SINGLE_45, result.format)
        assertEquals(RecordingSpeed.RPM_45, result.recordingSpeed)
        assertEquals(5, result.rating)
        assertEquals("/path/to/new.jpg", result.coverPath)
        assertEquals(ItemStatus.WISHLIST, result.status)
        assertEquals(createdAt, result.createdAt)
        assertEquals(updatedInstant, result.updatedAt)
    }

    @Test
    fun `nulls out optional fields when blank`() = runTest {
        val repo = FakeRecordRepository().apply { upsert(seedRecord()) }
        val useCase = UpdateRecordUseCase(repo, clock)

        val result = useCase(
            existing = seedRecord(),
            input = RecordInput(
                artists = listOf("Artist"),
                title = "Title",
                genre = "  ",
                label = "",
                country = "  ",
                coverPath = null
            )
        )

        assertNull(result.genre)
        assertNull(result.label)
        assertNull(result.country)
        assertNull(result.coverPath)
    }

    @Test
    fun `empty artists list throws`() = runTest {
        val useCase = UpdateRecordUseCase(FakeRecordRepository(), clock)
        try {
            useCase(seedRecord(), RecordInput(artists = emptyList(), title = "T"))
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("artist"))
        }
    }

    @Test
    fun `rating outside 0_to_5 throws`() = runTest {
        val useCase = UpdateRecordUseCase(FakeRecordRepository(), clock)
        try {
            useCase(seedRecord(), RecordInput(artists = listOf("A"), title = "T", rating = 7))
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("rating"))
        }
    }
}
