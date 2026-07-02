package app.acervatim.domain.usecase

import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.CoverStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ImportScannedSeriesUseCaseTest {

    private val fixedInstant: Instant = Instant.parse("2026-06-29T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val records = FakeRecordRepository()
    private val series = FakeSeriesRepository(records)
    private val externalSearch = FakeExternalSearchRepository()
    private val coverStorage = FakeCoverStorage()
    private val useCase = ImportScannedSeriesUseCase(series, records, externalSearch, coverStorage, clock)

    private val match = ExternalMatch(
        source = "mal",
        sourceId = "23390",
        title = "Shingeki no Kyojin T.2",
        creators = listOf("Hajime Isayama"),
        coverUrl = "https://cdn.example/aot-2.jpg",
        seriesTitleFr = "L'attaque des titans",
        volumeNumber = 2,
        edition = null,
        totalVolumes = 34
    )

    private val mapping = EditionMapping(
        titleFr = "L'attaque des titans",
        edition = null,
        tomeCount = 3,
        tomes = listOf(
            EditionTome(editionVolume = 1, isbn = "111", titleFr = "L'attaque des titans T.1"),
            EditionTome(editionVolume = 2, isbn = "222", titleFr = "L'attaque des titans T.2"),
            EditionTome(editionVolume = 3, isbn = "333", titleFr = "L'attaque des titans T.3")
        ),
        ongoing = false
    )

    @Test
    fun `full import creates series and all tomes, scanned owned and rest wishlist`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)

        val result = useCase.importFromList(scannedIsbn = "222", match = match, importAllKnownTomes = true)

        assertEquals(3, result.createdVolumes)
        assertEquals(0, result.skippedExisting)
        assertFalse(result.partial)
        assertEquals("L'attaque des titans", externalSearch.lastEditionTitle)

        val all = records.listAll()
        assertEquals(3, all.size)
        assertEquals(ItemStatus.OWNED, all.first { it.isbn == "222" }.status)
        assertEquals(ItemStatus.WISHLIST, all.first { it.isbn == "111" }.status)
        assertEquals(ItemStatus.WISHLIST, all.first { it.isbn == "333" }.status)
        assertTrue(all.all { it.seriesId == result.seriesId })
        assertEquals(3, series.listAll().single().totalVolumes)
    }

    @Test
    fun `full import is idempotent on second pass`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)
        useCase.importFromList("222", match, importAllKnownTomes = true)

        val second = useCase.importFromList("222", match, importAllKnownTomes = true)

        assertEquals(0, second.createdVolumes)
        assertEquals(3, second.skippedExisting)
        assertEquals(3, records.listAll().size)
        assertEquals(1, series.listAll().size)
    }

    @Test
    fun `unchecked import creates series and only the scanned tome, no mapping call`() = runTest {
        val result = useCase.importFromList("222", match, importAllKnownTomes = false)

        assertEquals(1, result.createdVolumes)
        assertEquals(0, externalSearch.editionMappingCallCount)
        assertFalse(result.partial)
        val tome = records.listAll().single()
        assertEquals("222", tome.isbn)
        assertEquals(ItemStatus.OWNED, tome.status)
        assertEquals(result.seriesId, tome.seriesId)
    }

    @Test
    fun `import into existing series adds only the scanned tome without creating a series`() = runTest {
        val existing = Series(
            id = "series-1",
            collectionType = CollectionType.MANGA,
            title = "L'attaque des titans",
            originalAuthor = "Hajime Isayama",
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        series.upsert(existing)

        val result = useCase.importTomeIntoSeries("series-1", "222", match)

        assertEquals(1, result.createdVolumes)
        assertEquals(1, series.listAll().size)
        val tome = records.listAll().single()
        assertEquals("series-1", tome.seriesId)
        assertEquals(ItemStatus.OWNED, tome.status)
    }

    @Test
    fun `requested full import degrades to scanned tome when mapping fails`() = runTest {
        externalSearch.editionMappingResult = Result.failure(IllegalStateException("backend down"))

        val result = useCase.importFromList("222", match, importAllKnownTomes = true)

        assertEquals(1, result.createdVolumes)
        assertTrue(result.partial)
        assertEquals(ItemStatus.OWNED, records.listAll().single().status)
    }

    @Test
    fun `scanned tome absent from mapping is still added and owned`() = runTest {
        externalSearch.editionMappingResult = Result.success(
            mapping.copy(
                tomes = listOf(
                    EditionTome(1, "111", "T.1"),
                    EditionTome(3, "333", "T.3")
                )
            )
        )

        val result = useCase.importFromList("222", match, importAllKnownTomes = true)

        assertEquals(3, result.createdVolumes)
        val scanned = records.listAll().firstOrNull { it.isbn == "222" }
        assertNotNull(scanned)
        assertEquals(ItemStatus.OWNED, scanned!!.status)
    }

    @Test
    fun `scanning a tome already in wishlist flips it to owned`() = runTest {
        val existing = Series(
            id = "series-1",
            collectionType = CollectionType.MANGA,
            title = "L'attaque des titans",
            originalAuthor = "Hajime Isayama",
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        series.upsert(existing)
        useCase.importFromList("999", match.copy(volumeNumber = 9), importAllKnownTomes = false)
        // Crée un autre tome possédé; ajoutons un tome wishlist portant l'ISBN 222.
        records.upsert(
            app.acervatim.domain.model.Record(
                id = "wishlist-222",
                collectionType = CollectionType.MANGA,
                seriesId = "series-1",
                artists = listOf(app.acervatim.domain.model.Artist(id = "", name = "Hajime Isayama")),
                title = "T.2",
                isbn = "222",
                status = ItemStatus.WISHLIST,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )
        )

        val result = useCase.importTomeIntoSeries("series-1", "222", match)

        assertEquals(0, result.createdVolumes)
        assertEquals(1, result.skippedExisting)
        assertEquals(ItemStatus.OWNED, records.findByIsbn("222")!!.status)
    }

    @Test
    fun `scanned tome and new series cache the known cover, wishlist tomes do not`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)

        val result = useCase.importFromList("222", match, importAllKnownTomes = true)

        val scanned = records.findByIsbn("222")!!
        assertEquals("https://cdn.example/aot-2.jpg", scanned.coverRemoteUrl)
        assertEquals(coverStorage.pathFor(scanned.id), scanned.coverPath)

        // Les tomes de l'édition (wishlist) n'ont pas de jaquette connue : rien de mis en cache.
        val wishlist = records.findByIsbn("111")!!
        assertEquals(null, wishlist.coverRemoteUrl)
        assertEquals(null, wishlist.coverPath)

        // La série créée réutilise la jaquette du tome scanné.
        assertEquals(coverStorage.pathFor(result.seriesId), series.listAll().single().coverPath)
        // Une seule URL téléchargée, pour deux entités distinctes (série + tome scanné).
        assertEquals(2, coverStorage.saveUrlCalls.size)
        assertTrue(coverStorage.saveUrlCalls.all { it.second == "https://cdn.example/aot-2.jpg" })
    }

    @Test
    fun `cover download failure does not block the import`() = runTest {
        coverStorage.failOnSaveFromUrl = true

        val result = useCase.importFromList("222", match, importAllKnownTomes = false)

        assertEquals(1, result.createdVolumes)
        val scanned = records.findByIsbn("222")!!
        assertEquals(ItemStatus.OWNED, scanned.status)
        // URL distante conservée pour re-cache, chemin local absent (échec réseau).
        assertEquals("https://cdn.example/aot-2.jpg", scanned.coverRemoteUrl)
        assertEquals(null, scanned.coverPath)
        assertEquals(null, series.listAll().single().coverPath)
    }
}

private class FakeCoverStorage : CoverStorage {
    val saveUrlCalls = mutableListOf<Pair<String, String>>()
    var failOnSaveFromUrl = false

    override suspend fun saveFromUri(recordId: String, uriString: String): String = pathFor(recordId)
    override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = pathFor(recordId)
    override suspend fun saveFromUrl(recordId: String, url: String): String {
        if (failOnSaveFromUrl) throw java.io.IOException("network down")
        saveUrlCalls += recordId to url
        return pathFor(recordId)
    }
    override suspend fun delete(recordId: String) = Unit
    override fun pathFor(recordId: String): String = "/fake/covers/$recordId.jpg"
}
