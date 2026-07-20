package app.acervatim.domain.usecase

import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.CoverStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RefreshMetadataUseCaseTest {

    private val fixedInstant: Instant = Instant.parse("2026-07-20T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val records = FakeRecordRepository()
    private val series = FakeSeriesRepository(records)
    private val externalSearch = FakeExternalSearchRepository()
    private val coverStorage = RefreshFakeCoverStorage()
    private val useCase = RefreshMetadataUseCase(records, series, externalSearch, coverStorage, clock)

    // ---- Tome manga ----------------------------------------------------------------------------

    @Test
    fun `manga tome without cover resolves it by ISBN and caches it`() = runTest {
        externalSearch.coverByIsbn = mapOf("111" to "https://cdn.example/t1.jpg")
        // Description déjà présente → seule la jaquette est recherchée (via /cover, pas /search).
        records.upsert(mangaTome(id = "t1", isbn = "111", synopsis = "déjà là"))

        val result = useCase(recordIds = listOf("t1"))

        assertEquals(listOf("t1"), result.refreshed)
        val tome = records.findByIsbn("111")!!
        assertEquals("https://cdn.example/t1.jpg", tome.coverRemoteUrl)
        assertEquals(coverStorage.pathFor("t1"), tome.coverPath)
        assertTrue(externalSearch.resolveCoverCalls.contains("111"))
        assertEquals(0, externalSearch.callCount) // aucun /search : la description existait déjà
    }

    @Test
    fun `manga tome that already has a cover and description is left unchanged with no network call`() =
        runTest {
            records.upsert(mangaTome(id = "t1", isbn = "111", coverPath = "/local/t1.jpg", synopsis = "d"))

            val result = useCase(recordIds = listOf("t1"))

            assertEquals(listOf("t1"), result.unchanged)
            assertTrue(result.refreshed.isEmpty())
            assertEquals(0, externalSearch.callCount)
            assertTrue(externalSearch.resolveCoverCalls.isEmpty())
        }

    @Test
    fun `manga tome with empty synopsis gets its description filled via search by ISBN`() = runTest {
        externalSearch.result = Result.success(
            listOf(externalMatch(description = "Résumé du tome 1"))
        )
        // Jaquette déjà présente → seule la description est recherchée.
        records.upsert(mangaTome(id = "t1", isbn = "111", coverPath = "/local/t1.jpg"))

        val result = useCase(recordIds = listOf("t1"))

        assertEquals(listOf("t1"), result.refreshed)
        assertEquals("Résumé du tome 1", records.findByIsbn("111")!!.extras[MangaExtras.KEY_TOME_SYNOPSIS])
        assertEquals(1, externalSearch.callCount)
        assertEquals("manga", externalSearch.lastType)
    }

    @Test
    fun `overwrite replaces an existing cover from the source`() = runTest {
        externalSearch.result = Result.success(
            listOf(externalMatch(coverUrl = "https://cdn.example/new.jpg", description = "d"))
        )
        records.upsert(
            mangaTome(id = "t1", isbn = "111", coverPath = "/local/old.jpg", coverRemoteUrl = "https://old", synopsis = "old")
        )

        val result = useCase(recordIds = listOf("t1"), options = RefreshOptions(overwrite = true))

        assertEquals(listOf("t1"), result.refreshed)
        val tome = records.findByIsbn("111")!!
        assertEquals("https://cdn.example/new.jpg", tome.coverRemoteUrl)
        assertEquals(coverStorage.pathFor("t1"), tome.coverPath)
        assertEquals("d", tome.extras[MangaExtras.KEY_TOME_SYNOPSIS])
    }

    @Test
    fun `cover resolution forwards the intitle fallback hint (series title, volume, edition)`() = runTest {
        externalSearch.coverByIsbn = mapOf("555" to "https://cdn.example/t5.jpg")
        series.upsert(seriesNode(id = "s1", title = "L'attaque des titans"))
        records.upsert(
            mangaTome(id = "t5", isbn = "555", seriesId = "s1", volume = 5, edition = "Éd. colossale", synopsis = "d")
        )

        useCase(recordIds = listOf("t5"))

        assertEquals(
            Triple<String?, Int?, String?>("L'attaque des titans", 5, "Éd. colossale"),
            externalSearch.lastResolveCoverHint
        )
    }

    // ---- Vinyle --------------------------------------------------------------------------------

    @Test
    fun `vinyl without cover gets one from Discogs by barcode (cover only in V1)`() = runTest {
        externalSearch.result = Result.success(
            listOf(externalMatch(coverUrl = "https://cdn.example/disc.jpg", description = "notes back-cover"))
        )
        records.upsert(vinyl(id = "v1", barcode = "0888072"))

        val result = useCase(recordIds = listOf("v1"))

        assertEquals(listOf("v1"), result.refreshed)
        val disc = records.listAll().single()
        assertEquals("https://cdn.example/disc.jpg", disc.coverRemoteUrl)
        assertEquals(coverStorage.pathFor("v1"), disc.coverPath)
        assertEquals("vinyl", externalSearch.lastType)
        // Pas de champ description vinyle : la description du match n'est pas persistée (V1).
        assertTrue(disc.extras.isEmpty())
    }

    @Test
    fun `target without an identifier is left unchanged with no call`() = runTest {
        records.upsert(vinyl(id = "v1", barcode = null))
        records.upsert(mangaTome(id = "t1", isbn = null))

        val result = useCase(recordIds = listOf("v1", "t1"))

        assertEquals(setOf("v1", "t1"), result.unchanged.toSet())
        assertTrue(result.refreshed.isEmpty())
        assertEquals(0, externalSearch.callCount)
        assertTrue(externalSearch.resolveCoverCalls.isEmpty())
    }

    @Test
    fun `a network failure marks the target failed without interrupting the batch`() = runTest {
        // Le /search échoue (impacte le vinyle) ; le tome, lui, comble sa jaquette via /cover (mécanisme distinct).
        externalSearch.result = Result.failure(java.io.IOException("discogs down"))
        externalSearch.coverByIsbn = mapOf("111" to "https://cdn.example/t1.jpg")
        records.upsert(vinyl(id = "v1", barcode = "0888072"))
        records.upsert(mangaTome(id = "t1", isbn = "111", synopsis = "d"))

        val result = useCase(recordIds = listOf("v1", "t1"))

        assertEquals(listOf("v1"), result.failed)
        assertEquals(listOf("t1"), result.refreshed)
    }

    // ---- Série ---------------------------------------------------------------------------------

    @Test
    fun `refreshing a series enumerates once, fills the header cover and existing tomes, creates nothing`() =
        runTest {
            externalSearch.editionMappingResult = Result.success(
                EditionMapping(
                    titleFr = "L'attaque des titans",
                    edition = null,
                    tomeCount = 3,
                    tomes = listOf(
                        EditionTome(1, "111", "T.1", coverUrl = "https://cdn.example/aot-1.jpg", description = "Résumé 1"),
                        EditionTome(2, "222", "T.2", coverUrl = "https://cdn.example/aot-2.jpg", description = "Résumé 2"),
                        EditionTome(3, "333", "T.3")
                    ),
                    ongoing = false
                )
            )
            series.upsert(seriesNode(id = "s1", title = "L'attaque des titans"))
            records.upsert(mangaTome(id = "t1", isbn = "111", seriesId = "s1", volume = 1))
            records.upsert(mangaTome(id = "t2", isbn = "222", seriesId = "s1", volume = 2))

            val result = useCase(seriesIds = listOf("s1"))

            assertEquals(1, externalSearch.editionMappingCallCount)
            // Header : jaquette du 1er tome connu.
            assertEquals(coverStorage.pathFor("s1"), series.listAll().single().coverPath)
            // Tomes comblés (jaquette + description).
            val t1 = records.findByIsbn("111")!!
            assertEquals("https://cdn.example/aot-1.jpg", t1.coverRemoteUrl)
            assertEquals("Résumé 1", t1.extras[MangaExtras.KEY_TOME_SYNOPSIS])
            val t2 = records.findByIsbn("222")!!
            assertEquals("Résumé 2", t2.extras[MangaExtras.KEY_TOME_SYNOPSIS])
            // Aucune création : toujours 2 tomes (le tome 3 du mapping n'est pas importé).
            assertEquals(2, records.listAll().size)
            assertTrue(result.refreshed.containsAll(listOf("s1", "t1", "t2")))
        }

    @Test
    fun `series refresh reports failed when edition-mapping is unavailable`() = runTest {
        externalSearch.editionMappingResult = Result.failure(IllegalStateException("backend down"))
        series.upsert(seriesNode(id = "s1", title = "X"))

        val result = useCase(seriesIds = listOf("s1"))

        assertEquals(listOf("s1"), result.failed)
    }

    // ---- Builders ------------------------------------------------------------------------------

    private fun mangaTome(
        id: String,
        isbn: String?,
        seriesId: String? = null,
        volume: Int? = null,
        coverPath: String? = null,
        coverRemoteUrl: String? = null,
        synopsis: String? = null,
        edition: String? = null
    ): Record = Record(
        id = id,
        collectionType = CollectionType.MANGA,
        seriesId = seriesId,
        volumeNumber = volume,
        artists = listOf(Artist(id = "", name = "Auteur")),
        title = "Tome $id",
        isbn = isbn,
        coverPath = coverPath,
        coverRemoteUrl = coverRemoteUrl,
        extras = buildMap {
            synopsis?.let { put(MangaExtras.KEY_TOME_SYNOPSIS, it) }
            edition?.let { put(MangaExtras.KEY_EDITION, it) }
        },
        createdAt = fixedInstant,
        updatedAt = fixedInstant
    )

    private fun vinyl(id: String, barcode: String?, coverPath: String? = null): Record = Record(
        id = id,
        collectionType = CollectionType.VINYL,
        artists = listOf(Artist(id = "", name = "Artiste")),
        title = "Disque $id",
        barcode = barcode,
        coverPath = coverPath,
        createdAt = fixedInstant,
        updatedAt = fixedInstant
    )

    private fun seriesNode(id: String, title: String): Series = Series(
        id = id,
        collectionType = CollectionType.MANGA,
        title = title,
        originalAuthor = "Auteur",
        createdAt = fixedInstant,
        updatedAt = fixedInstant
    )

    private fun externalMatch(coverUrl: String? = null, description: String? = null): ExternalMatch =
        ExternalMatch(
            source = "test",
            sourceId = "1",
            title = "Match",
            coverUrl = coverUrl,
            description = description
        )
}

private class RefreshFakeCoverStorage : CoverStorage {
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
