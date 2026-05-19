package fr.beudaert.acervus.domain.usecase

import app.cash.turbine.test
import fr.beudaert.acervus.domain.FakeRecordRepository
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.RecordingSpeed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AddRecordUseCaseTest {

    private val fixedInstant: Instant = Instant.parse("2026-05-16T12:34:56Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun `adds record with required fields and stamps timestamps`() = runTest {
        val repo = FakeRecordRepository()
        val useCase = AddRecordUseCase(repo, clock)

        val record = useCase(
            RecordInput(
                artists = listOf("  Pink Floyd  "),
                title = "  The Dark Side of the Moon  ",
                format = AcervusFormat.LP_33,
                recordingSpeed = RecordingSpeed.RPM_33,
                year = 1973
            )
        )

        assertEquals(listOf("Pink Floyd"), record.artists.map { it.name })
        assertEquals("The Dark Side of the Moon", record.title)
        assertEquals(AcervusFormat.LP_33, record.format)
        assertEquals(RecordingSpeed.RPM_33, record.recordingSpeed)
        assertEquals(1973, record.year)
        assertEquals(ItemStatus.OWNED, record.status)
        assertEquals(fixedInstant, record.createdAt)
        assertEquals(fixedInstant, record.updatedAt)
        assertNull(record.lastPlayedAt)
        assertNotNull(record.id)
    }

    @Test
    fun `stores multiple artists in input order`() = runTest {
        val repo = FakeRecordRepository()
        val useCase = AddRecordUseCase(repo, clock)

        val record = useCase(
            RecordInput(
                artists = listOf("Daft Punk", "Pharrell Williams", "Nile Rodgers"),
                title = "Get Lucky"
            )
        )

        assertEquals(
            listOf("Daft Punk", "Pharrell Williams", "Nile Rodgers"),
            record.artists.map { it.name }
        )
        assertEquals("Daft Punk", record.primaryArtist?.name)
        assertEquals(2, record.featuringArtists.size)
    }

    @Test
    fun `dedupes artist entries`() = runTest {
        val repo = FakeRecordRepository()
        val useCase = AddRecordUseCase(repo, clock)

        val record = useCase(
            RecordInput(
                artists = listOf("Artist", "Other", "Artist"),
                title = "T"
            )
        )

        assertEquals(listOf("Artist", "Other"), record.artists.map { it.name })
    }

    @Test
    fun `blank optional fields are normalized to null`() = runTest {
        val repo = FakeRecordRepository()
        val useCase = AddRecordUseCase(repo, clock)

        val record = useCase(
            RecordInput(
                artists = listOf("Artist"),
                title = "Title",
                genre = "  ",
                label = "",
                country = "   "
            )
        )

        assertNull(record.genre)
        assertNull(record.label)
        assertNull(record.country)
    }

    @Test
    fun `record is persisted in repository and observable`() = runTest {
        val repo = FakeRecordRepository()
        val useCase = AddRecordUseCase(repo, clock)

        repo.observeAll().test {
            assertEquals(emptyList<Any>(), awaitItem())

            useCase(RecordInput(artists = listOf("Artist"), title = "Title"))

            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("Artist", emitted.first().primaryArtist?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty artists list throws`() = runTest {
        val useCase = AddRecordUseCase(FakeRecordRepository(), clock)
        try {
            useCase(RecordInput(artists = emptyList(), title = "Title"))
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("artist"))
        }
    }

    @Test
    fun `only blank artists throws`() = runTest {
        val useCase = AddRecordUseCase(FakeRecordRepository(), clock)
        try {
            useCase(RecordInput(artists = listOf("   ", ""), title = "Title"))
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("artist"))
        }
    }

    @Test
    fun `blank title throws`() = runTest {
        val useCase = AddRecordUseCase(FakeRecordRepository(), clock)
        try {
            useCase(RecordInput(artists = listOf("Artist"), title = ""))
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("title"))
        }
    }
}
