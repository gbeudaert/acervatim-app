package fr.beudaert.acervatim.ui.scan

import fr.beudaert.acervatim.domain.FakeRecordRepository
import fr.beudaert.acervatim.domain.model.Artist
import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.ItemStatus
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
import java.time.Instant

class BarcodeScanViewModelTest {

    private val now: Instant = Instant.parse("2026-05-17T12:00:00Z")
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRecordRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRecordRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): BarcodeScanViewModel = BarcodeScanViewModel(repository)

    @Test
    fun `permission result updates state`() {
        val v = vm()
        assertTrue(!v.state.value.permissionResolved)
        v.onCameraPermissionResult(true)
        assertTrue(v.state.value.permissionResolved)
        assertTrue(v.state.value.permissionGranted)
    }

    @Test
    fun `existing barcode emits open record event`() = runTest {
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
    }

    @Test
    fun `unknown barcode emits propose new event`() = runTest {
        val v = vm()
        v.onBarcodeDetected("9999999999999")
        advanceUntilIdle()

        val event = v.events.value
        assertTrue(event is BarcodeScanEvent.ProposeNewRecord)
        assertEquals("9999999999999", (event as BarcodeScanEvent.ProposeNewRecord).barcode)
        assertTrue(v.state.value.detection is DetectionStep.NotInCollection)
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
