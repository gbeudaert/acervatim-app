package app.acervatim.ui.series

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ImportJob
import app.acervatim.domain.model.ImportJobProgress
import app.acervatim.domain.model.ImportJobState
import app.acervatim.domain.model.ImportPhase
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.usecase.ImportScannedSeriesUseCase
import app.acervatim.domain.usecase.WarmEditionImportUseCase
import app.acervatim.ui.scan.ScanPrefillHandoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SeriesImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-29T10:00:00Z"), ZoneOffset.UTC)

    private lateinit var records: FakeRecordRepository
    private lateinit var series: FakeSeriesRepository
    private lateinit var search: FakeExternalSearchRepository
    private lateinit var handoff: ScanPrefillHandoff

    private val match = ExternalMatch(
        source = "mal",
        sourceId = "23390",
        title = "Shingeki no Kyojin T.2",
        creators = listOf("Hajime Isayama"),
        seriesTitleFr = "L'attaque des titans",
        volumeNumber = 2,
        totalVolumes = 34
    )

    private val mapping = EditionMapping(
        titleFr = "L'attaque des titans",
        edition = null,
        tomeCount = 3,
        tomes = listOf(
            EditionTome(editionVolume = 1, isbn = "111", titleFr = "T.1"),
            EditionTome(editionVolume = 2, isbn = "222", titleFr = "T.2"),
            EditionTome(editionVolume = 3, isbn = "333", titleFr = "T.3")
        ),
        ongoing = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        records = FakeRecordRepository()
        series = FakeSeriesRepository(records)
        search = FakeExternalSearchRepository()
        handoff = ScanPrefillHandoff()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(barcode: String? = "222", withMatch: Boolean = true): SeriesImportViewModel {
        if (withMatch && barcode != null) handoff.put(barcode, match)
        val saved = SavedStateHandle(buildMap { barcode?.let { put("barcode", it) } })
        val useCase = ImportScannedSeriesUseCase(series, records, search, NoopCoverStorage, clock)
        return SeriesImportViewModel(saved, handoff, WarmEditionImportUseCase(search), useCase)
    }

    @Test
    fun `without match the screen offers manual entry`() {
        val v = vm(withMatch = false)
        assertFalse(v.state.value.hasMatch)
        assertNull(v.state.value.seriesTitle)
    }

    @Test
    fun `with match it exposes series title and scanned volume`() {
        val v = vm()
        assertTrue(v.state.value.hasMatch)
        assertEquals("L'attaque des titans", v.state.value.seriesTitle)
        assertEquals(2, v.state.value.scannedVolume)
        assertTrue(v.state.value.importAll)
    }

    @Test
    fun `toggling import all updates the state`() {
        val v = vm()
        v.onToggleImportAll(false)
        assertFalse(v.state.value.importAll)
    }

    @Test
    fun `import all creates series and every known tome`() = runTest {
        search.editionMappingResult = Result.success(mapping)
        val v = vm()

        v.onImport()
        advanceUntilIdle()

        val result = v.state.value.result
        assertNotNull(result)
        assertEquals(3, result!!.createdVolumes)
        assertFalse(v.state.value.importing)
        assertEquals(3, records.listAll().size)
        assertEquals("L'attaque des titans", search.lastEditionTitle)
    }

    @Test
    fun `unchecked import creates only the scanned tome and skips mapping`() = runTest {
        val v = vm()
        v.onToggleImportAll(false)

        v.onImport()
        advanceUntilIdle()

        assertEquals(1, v.state.value.result?.createdVolumes)
        assertEquals(0, search.editionMappingCallCount)
    }

    @Test
    fun `import is a no-op once a result is present`() = runTest {
        search.editionMappingResult = Result.success(mapping)
        val v = vm()
        v.onImport()
        advanceUntilIdle()
        val createdAfterFirst = records.listAll().size

        v.onImport()
        advanceUntilIdle()

        assertEquals(createdAfterFirst, records.listAll().size)
    }

    @Test
    fun `import all polls the async job then imports from warm cache`() = runTest {
        search.editionMappingResult = Result.success(mapping)
        search.startImportJobResult = Result.success(ImportJob("j", ImportJobState.QUEUED))
        search.importJobStatusSequence = listOf(
            Result.success(
                ImportJob("j", ImportJobState.RUNNING, ImportJobProgress(ImportPhase.COVERS, 3, 10))
            ),
            Result.success(ImportJob("j", ImportJobState.DONE))
        )
        val v = vm()

        v.onImport()
        advanceUntilIdle()

        assertEquals(1, search.startImportJobCallCount)
        assertTrue("job should have been polled", search.importJobStatusCallCount >= 2)
        assertEquals(3, v.state.value.result?.createdVolumes)
        assertFalse(v.state.value.importing)
        // Cache réchauffé : l'import local re-tape bien `edition-mapping`.
        assertEquals("L'attaque des titans", search.lastEditionTitle)
    }

    @Test
    fun `job with done state skips polling`() = runTest {
        search.editionMappingResult = Result.success(mapping)
        search.startImportJobResult = Result.success(ImportJob("j", ImportJobState.DONE))
        val v = vm()

        v.onImport()
        advanceUntilIdle()

        assertEquals(0, search.importJobStatusCallCount)
        assertEquals(3, v.state.value.result?.createdVolumes)
    }

    @Test
    fun `job start failure degrades to scanned tome only`() = runTest {
        // `editionMappingResult` reste en échec (défaut) → dégradation attendue.
        search.startImportJobResult = Result.failure(IllegalStateException("redis down"))
        val v = vm()

        v.onImport()
        advanceUntilIdle()

        assertEquals(0, search.importJobStatusCallCount)
        val result = v.state.value.result
        assertNotNull(result)
        assertTrue(result!!.degradedFullImport)
    }

    @Test
    fun `failed job degrades to scanned tome only`() = runTest {
        search.startImportJobResult = Result.success(ImportJob("j", ImportJobState.QUEUED))
        search.importJobStatusSequence = listOf(Result.success(ImportJob("j", ImportJobState.FAILED)))
        val v = vm()

        v.onImport()
        advanceUntilIdle()

        assertTrue(v.state.value.result?.degradedFullImport == true)
    }

    @Test
    fun `import does nothing without a match`() = runTest {
        val v = vm(withMatch = false)

        v.onImport()
        advanceUntilIdle()

        assertNull(v.state.value.result)
        assertTrue(records.listAll().isEmpty())
    }
}

private object NoopCoverStorage : CoverStorage {
    override suspend fun saveFromUri(recordId: String, uriString: String): String = pathFor(recordId)
    override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = pathFor(recordId)
    override suspend fun saveFromUrl(recordId: String, url: String): String = pathFor(recordId)
    override suspend fun delete(recordId: String) = Unit
    override fun pathFor(recordId: String): String = "/fake/covers/$recordId.jpg"
}
