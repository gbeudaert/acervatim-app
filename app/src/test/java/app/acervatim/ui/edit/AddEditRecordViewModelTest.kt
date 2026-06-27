package app.acervatim.ui.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.usecase.AddRecordUseCase
import app.acervatim.domain.usecase.ObserveArtistSuggestionsUseCase
import app.acervatim.domain.usecase.ObserveRecordByIdUseCase
import app.acervatim.domain.usecase.UpdateRecordUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import app.acervatim.ui.scan.ScanPrefillHandoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddEditRecordViewModelTest {

    private val now: Instant = Instant.parse("2026-05-17T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    private lateinit var repository: FakeRecordRepository
    private lateinit var addRecord: AddRecordUseCase
    private lateinit var updateRecord: UpdateRecordUseCase
    private lateinit var observeRecord: ObserveRecordByIdUseCase
    private lateinit var observeSuggestions: ObserveArtistSuggestionsUseCase
    private lateinit var coverStorage: FakeCoverStorage
    private lateinit var prefillHandoff: ScanPrefillHandoff

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRecordRepository()
        addRecord = AddRecordUseCase(repository, clock)
        updateRecord = UpdateRecordUseCase(repository, clock)
        observeRecord = ObserveRecordByIdUseCase(repository)
        observeSuggestions = ObserveArtistSuggestionsUseCase(repository)
        coverStorage = FakeCoverStorage()
        prefillHandoff = ScanPrefillHandoff()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        recordId: String? = null,
        barcode: String? = null
    ): AddEditRecordViewModel {
        val handle = SavedStateHandle().apply {
            if (recordId != null) set(AcervatimNavArgs.RECORD_ID, recordId)
            if (barcode != null) set(AcervatimNavArgs.BARCODE, barcode)
        }
        return AddEditRecordViewModel(
            savedStateHandle = handle,
            observeRecord = observeRecord,
            observeArtistSuggestions = observeSuggestions,
            addRecord = addRecord,
            updateRecord = updateRecord,
            coverStorage = coverStorage,
            prefillHandoff = prefillHandoff
        )
    }

    @Test
    fun `new record save creates persisted record with generated id`() = runTest {
        val vm = viewModel()
        vm.onArtistInputChange("Pink Floyd")
        vm.onArtistCommit("Pink Floyd")
        vm.onTitleChange("The Wall")

        vm.save()
        advanceUntilIdle()

        var items: List<Record> = emptyList()
        repository.observeAll().test {
            items = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, items.size)
        val saved = items.first()
        assertEquals(listOf("Pink Floyd"), saved.artists.map { it.name })
        assertEquals("The Wall", saved.title)
        assertEquals(vm.formState.value.recordId, saved.id)
        assertEquals(now, saved.createdAt)
    }

    @Test
    fun `multi-artist save persists all artists in order`() = runTest {
        val vm = viewModel()
        vm.onArtistCommit("Daft Punk")
        vm.onArtistCommit("Pharrell Williams")
        vm.onArtistCommit("Nile Rodgers")
        vm.onTitleChange("Get Lucky")

        vm.save()
        advanceUntilIdle()

        var saved: Record? = null
        repository.observeAll().test {
            saved = awaitItem().single()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(
            listOf("Daft Punk", "Pharrell Williams", "Nile Rodgers"),
            saved!!.artists.map { it.name }
        )
    }

    @Test
    fun `pending input is committed on save`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Title")
        // User typed "Artist" but didn't press Enter.
        vm.onArtistInputChange("Artist")

        vm.save()
        advanceUntilIdle()

        var saved: Record? = null
        repository.observeAll().test {
            saved = awaitItem().single()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("Artist"), saved!!.artists.map { it.name })
    }

    @Test
    fun `duplicate artist commit is ignored`() = runTest {
        val vm = viewModel()
        vm.onArtistCommit("Artist")
        vm.onArtistCommit("artist") // case-insensitive duplicate
        assertEquals(listOf("Artist"), vm.formState.value.artists)
    }

    @Test
    fun `artist remove drops the chip at the given index`() = runTest {
        val vm = viewModel()
        vm.onArtistCommit("A")
        vm.onArtistCommit("B")
        vm.onArtistCommit("C")
        vm.onArtistRemove(1)
        assertEquals(listOf("A", "C"), vm.formState.value.artists)
    }

    @Test
    fun `editing existing record populates form and updates without changing createdAt`() = runTest {
        val createdAt = Instant.parse("2025-01-01T00:00:00Z")
        val existing = Record(
            id = "rec-42",
            artists = listOf(Artist("a", "Old"), Artist("b", "Feat")),
            title = "Old Title",
            year = 2000,
            format = AcervatimFormat.LP_33,
            status = ItemStatus.OWNED,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        repository.upsert(existing)

        val vm = viewModel(recordId = "rec-42")
        advanceUntilIdle()

        val loaded = vm.formState.value
        assertEquals(listOf("Old", "Feat"), loaded.artists)
        assertEquals("Old Title", loaded.title)
        assertEquals("2000", loaded.year)
        assertEquals(AcervatimFormat.LP_33, loaded.format)
        assertTrue(loaded.isEditing)

        vm.onTitleChange("Brand New Title")
        vm.save()
        advanceUntilIdle()

        var updated: Record? = null
        repository.observeById("rec-42").test {
            updated = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertNotNull(updated)
        assertEquals("Brand New Title", updated!!.title)
        assertEquals(createdAt, updated!!.createdAt)
        assertEquals(now, updated!!.updatedAt)
    }

    @Test
    fun `cover picked is persisted via CoverStorage on save`() = runTest {
        val vm = viewModel()
        vm.onArtistCommit("Artist")
        vm.onTitleChange("Title")
        vm.onCoverPicked("content://test/photo.jpg")

        vm.save()
        advanceUntilIdle()

        assertEquals(1, coverStorage.saveCalls.size)
        val (recordId, uri) = coverStorage.saveCalls.single()
        assertEquals(vm.formState.value.recordId, recordId)
        assertEquals("content://test/photo.jpg", uri)

        var list: List<Record> = emptyList()
        repository.observeAll().test {
            list = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(coverStorage.pathFor(recordId), list.single().coverPath)
    }

    @Test
    fun `cover removed clears persisted cover path on save`() = runTest {
        val existing = Record(
            id = "rec-1",
            artists = listOf(Artist("a", "Artist")),
            title = "Title",
            coverPath = "/some/old.jpg",
            status = ItemStatus.OWNED,
            createdAt = now,
            updatedAt = now
        )
        repository.upsert(existing)

        val vm = viewModel(recordId = "rec-1")
        advanceUntilIdle()
        assertEquals("/some/old.jpg", vm.formState.value.persistedCoverPath)

        vm.onCoverRemoved()
        vm.save()
        advanceUntilIdle()

        var saved: Record? = null
        repository.observeById("rec-1").test {
            saved = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(saved?.coverPath)
        assertTrue(coverStorage.saveCalls.isEmpty())
    }

    @Test
    fun `nav barcode pre-fills field for new record`() = runTest {
        val vm = viewModel(barcode = "0123456789012")
        assertEquals("0123456789012", vm.formState.value.barcode)
        assertTrue(!vm.formState.value.isEditing)
    }

    @Test
    fun `nav barcode is ignored when editing existing record`() = runTest {
        val existing = Record(
            id = "rec-1",
            artists = listOf(Artist("a", "Artist")),
            title = "Title",
            barcode = "9999999999999",
            status = ItemStatus.OWNED,
            createdAt = now,
            updatedAt = now
        )
        repository.upsert(existing)

        val vm = viewModel(recordId = "rec-1", barcode = "0000000000000")
        advanceUntilIdle()

        assertEquals("9999999999999", vm.formState.value.barcode)
    }

    @Test
    fun `save with invalid fields surfaces validation errors and does not persist`() = runTest {
        val vm = viewModel()
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.formState.value.showValidationErrors)
        var items: List<Record> = emptyList()
        repository.observeAll().test {
            items = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(items.isEmpty())
    }
}

private class FakeCoverStorage : CoverStorage {
    val saveCalls = mutableListOf<Pair<String, String>>()
    private val deleteCalls = mutableListOf<String>()

    override suspend fun saveFromUri(recordId: String, uriString: String): String {
        saveCalls += recordId to uriString
        return pathFor(recordId)
    }

    override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = pathFor(recordId)
    override suspend fun saveFromUrl(recordId: String, url: String): String = pathFor(recordId)
    override suspend fun delete(recordId: String) {
        deleteCalls += recordId
    }
    override fun pathFor(recordId: String): String = "/fake/covers/$recordId.jpg"
}
