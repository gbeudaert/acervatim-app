package app.acervatim.domain.usecase

import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.model.SeriesEditionOption
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
    fun `scanned tome uses the known cover without an extra cover lookup`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)

        val result = useCase.importFromList("222", match, importAllKnownTomes = true)

        val scanned = records.findByIsbn("222")!!
        assertEquals("https://cdn.example/aot-2.jpg", scanned.coverRemoteUrl)
        assertEquals(coverStorage.pathFor(scanned.id), scanned.coverPath)
        // Jaquette du tome scanné déjà connue (`/search`) : aucune résolution `/cover` pour son ISBN.
        assertFalse(externalSearch.resolveCoverCalls.contains("222"))

        // La série créée réutilise la jaquette du tome scanné.
        assertEquals(coverStorage.pathFor(result.seriesId), series.listAll().single().coverPath)
    }

    @Test
    fun `wishlist tomes resolve their cover by ISBN and cache it offline`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)
        externalSearch.coverByIsbn = mapOf(
            "111" to "https://cdn.example/aot-1.jpg",
            "333" to "https://cdn.example/aot-3.jpg"
        )

        useCase.importFromList("222", match, importAllKnownTomes = true)

        // Chaque tome sans jaquette connue est résolu par ISBN via `/cover`, puis caché localement.
        val tome1 = records.findByIsbn("111")!!
        assertEquals("https://cdn.example/aot-1.jpg", tome1.coverRemoteUrl)
        assertEquals(coverStorage.pathFor(tome1.id), tome1.coverPath)
        val tome3 = records.findByIsbn("333")!!
        assertEquals("https://cdn.example/aot-3.jpg", tome3.coverRemoteUrl)
        assertEquals(coverStorage.pathFor(tome3.id), tome3.coverPath)
        assertTrue(externalSearch.resolveCoverCalls.containsAll(listOf("111", "333")))
    }

    @Test
    fun `cover lookup forwards the intitle fallback hint (series title, volume, edition)`() = runTest {
        val colossal = match.copy(edition = "Éd. colossale")
        externalSearch.editionMappingResult = Result.success(
            mapping.copy(
                edition = "Éd. colossale",
                tomes = listOf(EditionTome(editionVolume = 5, isbn = "555", titleFr = "T.5"))
            )
        )

        // Tome 555 sans jaquette connue → `/cover`, armé du repli titre/tome/édition. Le tome scanné
        // (999) garde la jaquette du `/search`, donc n'appelle pas `/cover` : la résolution est unique.
        useCase.importFromList("999", colossal, importAllKnownTomes = true)

        assertTrue(externalSearch.resolveCoverCalls.contains("555"))
        assertEquals(
            Triple<String?, Int?, String?>("L'attaque des titans", 5, "Éd. colossale"),
            externalSearch.lastResolveCoverHint
        )
    }

    @Test
    fun `per-tome synopsis is stored in extras, empty tomes get no key (no series fallback)`() = runTest {
        externalSearch.editionMappingResult = Result.success(
            mapping.copy(
                tomes = listOf(
                    EditionTome(1, "111", "T.1", description = "Résumé propre au tome 1"),
                    EditionTome(2, "222", "T.2", description = null),
                    EditionTome(3, "333", "T.3", description = null)
                )
            )
        )

        useCase.importFromList("222", match, importAllKnownTomes = true)

        assertEquals(
            "Résumé propre au tome 1",
            records.findByIsbn("111")!!.extras[MangaExtras.KEY_TOME_SYNOPSIS]
        )
        // Aucun résumé par tome → clé absente : pas de repli sur le synopsis de série.
        assertFalse(records.findByIsbn("333")!!.extras.containsKey(MangaExtras.KEY_TOME_SYNOPSIS))
    }

    @Test
    fun `edition-mapping cover is used directly without a cover lookup`() = runTest {
        // Cache serveur chaud : `edition-mapping` porte déjà la jaquette du tome (pas d'appel `/cover`).
        externalSearch.editionMappingResult = Result.success(
            mapping.copy(
                tomes = listOf(
                    EditionTome(1, "111", "T.1", coverUrl = "https://cdn.example/warm-1.jpg"),
                    EditionTome(2, "222", "T.2"),
                    EditionTome(3, "333", "T.3")
                )
            )
        )

        useCase.importFromList("222", match, importAllKnownTomes = true)

        val tome1 = records.findByIsbn("111")!!
        assertEquals("https://cdn.example/warm-1.jpg", tome1.coverRemoteUrl)
        assertEquals(coverStorage.pathFor(tome1.id), tome1.coverPath)
        assertFalse(externalSearch.resolveCoverCalls.contains("111"))
    }

    @Test
    fun `cover resolution failure does not block the import`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)
        externalSearch.resolveCoverFailure = java.io.IOException("cover backend down")

        val result = useCase.importFromList("222", match, importAllKnownTomes = true)

        // Import complet malgré l'échec de résolution : tomes créés, jaquette absente (dégradation).
        assertEquals(3, result.createdVolumes)
        val tome1 = records.findByIsbn("111")!!
        assertEquals(ItemStatus.WISHLIST, tome1.status)
        assertEquals(null, tome1.coverRemoteUrl)
        assertEquals(null, tome1.coverPath)
        // Le tome scanné garde sa jaquette connue (`/search`), non impactée par l'échec `/cover`.
        assertEquals("https://cdn.example/aot-2.jpg", records.findByIsbn("222")!!.coverRemoteUrl)
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

    // ---- AD2 : l'édition dans l'identité, import par titre -------------------------------------

    private val candidate = SeriesCandidate(
        source = "mangadex",
        sourceId = "md-snk",
        mangaId = "md-snk",
        title = "L'attaque des titans",
        titleFr = "L'attaque des titans",
        titleRomaji = "Shingeki no Kyojin",
        authors = listOf("Hajime Isayama"),
        year = 2009,
        coverUrl = "https://uploads.example/snk.jpg",
        synopsis = "Les titans attaquent.",
        status = "completed",
        volumeCount = 34,
        malId = "23390",
        anilistId = "53390"
    )

    private val pikaStandard = SeriesEditionOption(
        seriesTitleFr = "L'attaque des titans",
        publisher = "Pika",
        edition = null,
        isArtEdition = false,
        tomeCount = 3,
        firstYear = "2013",
        lastYear = "2021",
        ongoing = false,
        hasSourceVolumeRange = false,
        sampleIsbn = "111"
    )

    @Test
    fun `import par titre cree la serie et tous ses tomes en wishlist`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)

        val result = useCase.importFromSearch(candidate, pikaStandard)

        assertEquals(3, result.createdVolumes)
        assertFalse(result.degradedFullImport)
        // Aucun tome possédé : c'est le besoin wishlist.
        assertTrue(records.listAll().all { it.status == ItemStatus.WISHLIST })
        // Les paramètres de l'édition repartent tels quels vers `edition-mapping`.
        assertEquals("L'attaque des titans", externalSearch.lastEditionTitle)
        assertEquals("Pika", externalSearch.lastEditionPublisher)
        assertEquals("md-snk", externalSearch.lastEditionMangaId)

        val created = series.listAll().single()
        assertEquals("Pika", created.publisherFr)
        assertEquals(null, created.edition)
        assertEquals("md-snk", created.mangaId)
        assertEquals("Hajime Isayama", created.originalAuthor)
        assertEquals("Pika", records.findByIsbn("111")!!.extras[MangaExtras.KEY_PUBLISHER_FR])
    }

    @Test
    fun `import par titre ne recree pas un tome deja present`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)
        useCase.importFromList("222", match.copy(publisherFr = "Pika éd."), importAllKnownTomes = false)

        val result = useCase.importFromSearch(candidate, pikaStandard)

        // Le tome 2 scanné existe déjà (dédup ISBN) et reste possédé ; les deux autres arrivent.
        assertEquals(2, result.createdVolumes)
        assertEquals(1, result.skippedExisting)
        assertEquals(ItemStatus.OWNED, records.findByIsbn("222")!!.status)
        // « Pika éd. » (scan) et « Pika » (recherche) sont le même éditeur : une seule série.
        assertEquals(1, series.listAll().size)
    }

    @Test
    fun `deux editions d'une meme serie donnent deux series`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)
        useCase.importFromSearch(candidate, pikaStandard)

        val colossale = mapping.copy(
            edition = "Éd. colossale",
            tomes = listOf(EditionTome(editionVolume = 1, isbn = "c-1", titleFr = "L'attaque des titans T.1"))
        )
        externalSearch.editionMappingResult = Result.success(colossale)
        useCase.importFromSearch(
            candidate,
            pikaStandard.copy(edition = "Éd. colossale", isArtEdition = true, tomeCount = 1)
        )

        val all = series.listAll()
        assertEquals(2, all.size)
        assertEquals(setOf(null, "Éd. colossale"), all.map { it.edition }.toSet())
        // Deux « tome 1 » sous deux séries distinctes : plus de 409 (série + volume) à la synchro.
        val tome1s = records.listAll().filter { it.volumeNumber == 1 }
        assertEquals(2, tome1s.map { it.seriesId }.distinct().size)
    }

    @Test
    fun `mapping indisponible - la serie est creee vide et le repli est signale`() = runTest {
        externalSearch.editionMappingResult = Result.failure(java.io.IOException("BnF down"))

        val result = useCase.importFromSearch(candidate, pikaStandard)

        assertEquals(0, result.createdVolumes)
        assertTrue(result.degradedFullImport)
        assertTrue(result.partial)
        assertEquals("Pika", series.listAll().single().publisherFr)
    }

    @Test
    fun `une serie d'avant la v8 est adoptee au lieu d'etre dupliquee`() = runTest {
        val legacy = Series(
            id = "legacy",
            collectionType = CollectionType.MANGA,
            title = "L'attaque des titans",
            originalAuthor = "Hajime Isayama",
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        series.upsert(legacy)

        val result = useCase.importFromList(
            "222",
            match.copy(publisherFr = "Pika édition"),
            importAllKnownTomes = false
        )

        assertEquals("legacy", result.seriesId)
        val adopted = series.listAll().single()
        assertEquals("Pika édition", adopted.publisherFr)
    }

    private suspend fun seedLegacyWithTome(extras: Map<String, String>) {
        series.upsert(
            Series(
                id = "legacy",
                collectionType = CollectionType.MANGA,
                title = "L'attaque des titans",
                originalAuthor = "Hajime Isayama",
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )
        )
        records.upsert(
            app.acervatim.domain.model.Record(
                id = "old-1",
                collectionType = CollectionType.MANGA,
                seriesId = "legacy",
                volumeNumber = 1,
                artists = emptyList(),
                title = "L'attaque des titans T.1",
                isbn = "old-isbn-1",
                status = ItemStatus.OWNED,
                extras = extras,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )
        )
    }

    @Test
    fun `une serie d'avant la v8 aux tomes Pika n'adopte pas une edition France Loisirs`() = runTest {
        seedLegacyWithTome(mapOf(MangaExtras.KEY_PUBLISHER_FR to "Pika édition"))

        val result = useCase.importFromList(
            "222",
            match.copy(publisherFr = "Éditions France loisirs"),
            importAllKnownTomes = false
        )

        // Une autre édition : nouvelle série, la série historique reste intacte.
        assertTrue(result.seriesId != "legacy")
        assertEquals(2, series.listAll().size)
        assertEquals(null, series.listAll().first { it.id == "legacy" }.publisherFr)
    }

    @Test
    fun `une serie d'avant la v8 aux tomes d'une autre edition n'est pas adoptee`() = runTest {
        seedLegacyWithTome(
            mapOf(MangaExtras.KEY_PUBLISHER_FR to "Pika", MangaExtras.KEY_EDITION to "Éd. colossale")
        )

        val result = useCase.importFromList("222", match.copy(publisherFr = "Pika"), importAllKnownTomes = false)

        assertTrue(result.seriesId != "legacy")
    }

    @Test
    fun `une serie d'avant la v8 aux tomes sans editeur est adoptee`() = runTest {
        seedLegacyWithTome(emptyMap())

        val result = useCase.importFromList("222", match.copy(publisherFr = "Pika"), importAllKnownTomes = false)

        assertEquals("legacy", result.seriesId)
        assertEquals("Pika", series.listAll().single().publisherFr)
    }

    @Test
    fun `une serie d'avant la v8 aux tomes du meme editeur, autre graphie, est adoptee`() = runTest {
        seedLegacyWithTome(mapOf(MangaExtras.KEY_PUBLISHER_FR to "Pika éd."))

        val result = useCase.importFromList("222", match.copy(publisherFr = "Pika édition"), importAllKnownTomes = false)

        assertEquals("legacy", result.seriesId)
    }

    @Test
    fun `le scan transmet editeur et mangaId a edition-mapping`() = runTest {
        externalSearch.editionMappingResult = Result.success(mapping)

        useCase.importFromList(
            "222",
            match.copy(publisherFr = "Pika", mangaId = "md-snk"),
            importAllKnownTomes = true
        )

        assertEquals("Pika", externalSearch.lastEditionPublisher)
        assertEquals("md-snk", externalSearch.lastEditionMangaId)
        assertEquals("md-snk", series.listAll().single().mangaId)
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
