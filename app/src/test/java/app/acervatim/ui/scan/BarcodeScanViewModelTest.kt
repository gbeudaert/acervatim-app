package app.acervatim.ui.scan

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.FakeAuthRepository
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.usecase.LookupByBarcodeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class BarcodeScanViewModelTest {

    private val now: Instant = Instant.parse("2026-05-17T12:00:00Z")
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRecordRepository
    private lateinit var search: FakeExternalSearchRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var handoff: ScanPrefillHandoff

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRecordRepository()
        search = FakeExternalSearchRepository()
        auth = FakeAuthRepository()
        handoff = ScanPrefillHandoff()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(collectionType: String? = null): BarcodeScanViewModel {
        val savedState = SavedStateHandle(
            buildMap { collectionType?.let { put("collectionType", it) } }
        )
        return BarcodeScanViewModel(
            savedStateHandle = savedState,
            recordRepository = repository,
            lookupByBarcode = LookupByBarcodeUseCase(search, auth),
            prefillHandoff = handoff
        )
    }

    @Test
    fun `permission result updates state`() {
        val v = vm()
        assertTrue(!v.state.value.permissionResolved)
        v.onCameraPermissionResult(true)
        assertTrue(v.state.value.permissionResolved)
        assertTrue(v.state.value.permissionGranted)
    }

    @Test
    fun `existing barcode emits open record event without remote lookup`() = runTest {
        val record = Record(
            id = "rec-1",
            artists = listOf(Artist("a", "Artist")),
            title = "Known Album",
            barcode = "0123456789012",
            status = ItemStatus.OWNED,
            createdAt = now,
            updatedAt = now
        )
        repository.upsert(record)

        val v = vm()
        v.onBarcodeDetected("0123456789012")
        advanceUntilIdle()

        val event = v.events.value
        assertTrue(event is BarcodeScanEvent.OpenExistingRecord)
        assertEquals("rec-1", (event as BarcodeScanEvent.OpenExistingRecord).recordId)
        assertTrue(v.state.value.detection is DetectionStep.Found)
        // La dédup locale est prioritaire : aucun appel réseau ne doit partir.
        assertEquals(0, search.callCount)
    }

    @Test
    fun `unknown barcode while signed out falls back to manual without lookup`() = runTest {
        val v = vm()
        v.onBarcodeDetected("9999999999999")
        advanceUntilIdle()

        val event = v.events.value
        assertTrue(event is BarcodeScanEvent.ProposeNewRecord)
        assertEquals("9999999999999", (event as BarcodeScanEvent.ProposeNewRecord).barcode)
        assertTrue(v.state.value.detection is DetectionStep.NotInCollection)
        assertEquals(0, search.callCount)
        assertNull(handoff.take("9999999999999"))
    }

    @Test
    fun `unknown barcode signed in with match stores prefill and enriches`() = runTest {
        auth.setSignedIn("user-1")
        val match = ExternalMatch(
            source = "discogs",
            sourceId = "r123",
            title = "Discovered Album",
            creators = listOf("New Artist"),
            year = 1999,
            coverUrl = "https://img/cover.jpg"
        )
        search.result = Result.success(listOf(match))

        val v = vm(collectionType = "VINYL")
        v.onBarcodeDetected("1234567890123")
        advanceUntilIdle()

        assertEquals("1234567890123", search.lastBarcode)
        assertEquals("vinyl", search.lastType)
        assertTrue(v.state.value.detection is DetectionStep.Enriched)
        val event = v.events.value
        assertTrue(event is BarcodeScanEvent.ProposeNewRecord)
        // Le match retenu est déposé pour le formulaire, associé au code-barres.
        assertEquals(match, handoff.take("1234567890123"))
    }

    @Test
    fun `signed in lookup error falls back to manual`() = runTest {
        auth.setSignedIn("user-1")
        search.result = Result.failure(IOException("offline"))

        val v = vm()
        v.onBarcodeDetected("5555555555555")
        advanceUntilIdle()

        assertEquals(1, search.callCount)
        assertTrue(v.state.value.detection is DetectionStep.NotInCollection)
        assertTrue(v.events.value is BarcodeScanEvent.ProposeNewRecord)
        assertNull(handoff.take("5555555555555"))
    }

    @Test
    fun `subsequent barcode detections are ignored until reset`() = runTest {
        repository.upsert(
            Record(
                id = "rec-1",
                artists = listOf(Artist("a", "Artist")),
                title = "First",
                barcode = "1111111111111",
                status = ItemStatus.OWNED,
                createdAt = now,
                updatedAt = now
            )
        )

        val v = vm()
        v.onBarcodeDetected("1111111111111")
        advanceUntilIdle()
        val firstEvent = v.events.value

        // second detection while still in non-Idle state must be a no-op
        v.onBarcodeDetected("2222222222222")
        advanceUntilIdle()
        assertEquals(firstEvent, v.events.value)

        v.reset()
        assertTrue(v.state.value.detection is DetectionStep.Idle)
        assertNull(v.events.value)
    }
}
