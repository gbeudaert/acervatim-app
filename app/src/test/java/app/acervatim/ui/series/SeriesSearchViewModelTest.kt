package app.acervatim.ui.series

import app.acervatim.domain.FakeAuthRepository
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.ImportJob
import app.acervatim.domain.model.ImportJobState
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.model.SeriesEditionOption
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.usecase.ImportScannedSeriesUseCase
import app.acervatim.domain.usecase.SearchMangaSeriesUseCase
import app.acervatim.domain.usecase.WarmEditionImportUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SeriesSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)

    private lateinit var search: FakeExternalSearchRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var records: FakeRecordRepository
    private lateinit var series: FakeSeriesRepository

    private val frieren = SeriesCandidate(
        source = "mangadex",
        sourceId = "md-frieren",
        mangaId = "md-frieren",
        title = "Sousou no Frieren",
        titleFr = null,
        titleRomaji = "Sousou no Frieren",
        authors = listOf("Yamada Kanehito", "Abe Tsukasa"),
        year = 2020,
        coverUrl = null,
        synopsis = null,
        status = "ongoing",
        volumeCount = null,
        malId = "126287",
        anilistId = null
    )

    private val kioon = SeriesEditionOption(
        seriesTitleFr = "Frieren",
        publisher = "Ki-oon",
        edition = null,
        isArtEdition = false,
        tomeCount = 2,
        firstYear = "2022",
        lastYear = "2026",
        ongoing = true,
        hasSourceVolumeRange = false,
        sampleIsbn = "f-1"
    )

    private val mapping = EditionMapping(
        titleFr = "Frieren",
        edition = null,
        tomeCount = 2,
        tomes = listOf(
            EditionTome(editionVolume = 1, isbn = "f-1", titleFr = "Frieren T.1"),
            EditionTome(editionVolume = 2, isbn = "f-2", titleFr = "Frieren T.2")
        ),
        ongoing = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        search = FakeExternalSearchRepository()
        auth = FakeAuthRepository()
        auth.setSignedIn("user-1")
        records = FakeRecordRepository()
        series = FakeSeriesRepository(records)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(): SeriesSearchViewModel = SeriesSearchViewModel(
        searchSeries = SearchMangaSeriesUseCase(search, auth),
        externalSearch = search,
        warmEditionImport = WarmEditionImportUseCase(search),
        importSeries = ImportScannedSeriesUseCase(series, records, search, NoopCovers, clock)
    )

    // ---- Étape 1 : la série -----------------------------------------------------------------

    @Test
    fun `une requete trop courte ne part pas sur le reseau`() = runTest {
        val v = vm()
        v.onQueryChange("fr")
        advanceUntilIdle()

        assertEquals(SeriesSearchStep.Idle, v.state.value.step)
        assertTrue(search.seriesQueries.isEmpty())
    }

    @Test
    fun `une saisie tapee caractere par caractere ne declenche qu'une requete`() = runTest {
        val v = vm()
        listOf("f", "fr", "fri", "frie", "frier").forEach { typed ->
            v.onQueryChange(typed)
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(listOf("frier"), search.seriesQueries)
    }

    @Test
    fun `les candidats sont exposes dans l'ordre du serveur`() = runTest {
        val crossover = frieren.copy(sourceId = "md-x", mangaId = "md-x", title = "Frieren Cinnamoroll")
        search.seriesSearchResult = Result.success(listOf(frieren, crossover))
        val v = vm()
        v.onQueryChange("Frieren")
        advanceUntilIdle()

        val step = v.state.value.step as SeriesSearchStep.Results
        assertEquals(listOf("md-frieren", "md-x"), step.candidates.map { it.sourceId })
        assertEquals(SeriesSearchStage.SEARCH, v.state.value.stage)
    }

    @Test
    fun `aucun candidat donne NoMatch, pas une liste vide`() = runTest {
        val v = vm()
        v.onQueryChange("zzzzzz")
        advanceUntilIdle()

        assertEquals(SeriesSearchStep.NoMatch, v.state.value.step)
    }

    @Test
    fun `sans session on ne cherche pas`() = runTest {
        auth.signOut()
        val v = vm()
        v.onQueryChange("Frieren")
        advanceUntilIdle()

        assertEquals(SeriesSearchStep.NotConnected, v.state.value.step)
        assertTrue(search.seriesQueries.isEmpty())
    }

    @Test
    fun `le refus de jeton du repli MAL reste distinct d'une absence de resultat`() = runTest {
        search.seriesSearchResult = Result.failure(
            ExternalSearchException(ExternalSearchFailure.SOURCE_TOKEN_REQUIRED, provider = "mal")
        )
        val v = vm()
        v.onQueryChange("Frieren")
        advanceUntilIdle()

        assertEquals(
            SeriesSearchStep.Failed(ExternalSearchFailure.SOURCE_TOKEN_REQUIRED, "mal"),
            v.state.value.step
        )
    }

    // ---- Étape 2 : l'édition ----------------------------------------------------------------

    @Test
    fun `choisir une serie charge ses editions avec la saisie en indice de titre`() = runTest {
        search.seriesSearchResult = Result.success(listOf(frieren))
        search.editionsResult = Result.success(listOf(kioon))
        val v = vm()
        v.onQueryChange("Frieren")
        advanceUntilIdle()

        v.onSelectCandidate(frieren)
        assertEquals(EditionsStep.Loading, v.state.value.editions)
        advanceUntilIdle()

        assertEquals(SeriesSearchStage.EDITIONS, v.state.value.stage)
        assertEquals(EditionsStep.Loaded(listOf(kioon)), v.state.value.editions)
        assertEquals("md-frieren", search.lastEditionsMangaId)
        // Frieren n'a pas de titre FR chez MangaDex : la saisie est l'indice que la BnF connaît.
        assertEquals("Frieren", search.lastEditionsTitle)
    }

    @Test
    fun `aucune edition propose la creation manuelle pre-remplie`() = runTest {
        search.editionsResult = Result.success(emptyList())
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()

        assertEquals(EditionsStep.Empty, v.state.value.editions)
        v.onCreateManually()
        assertEquals(
            SeriesSearchEvent.CreateManually(title = "Sousou no Frieren", author = "Yamada Kanehito"),
            v.events.value
        )
        v.onEventConsumed()
        assertNull(v.events.value)
    }

    @Test
    fun `un candidat sans id MangaDex va directement a la creation manuelle`() = runTest {
        val fromMal = frieren.copy(source = "mal", sourceId = "126287", mangaId = null)
        val v = vm()
        v.onSelectCandidate(fromMal)
        advanceUntilIdle()

        assertEquals(EditionsStep.Empty, v.state.value.editions)
        assertNull(search.lastEditionsMangaId)
    }

    @Test
    fun `un echec des editions est retentable`() = runTest {
        search.editionsResult = Result.failure(ExternalSearchException(ExternalSearchFailure.NETWORK, cause = IOException()))
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()
        assertEquals(EditionsStep.Failed(ExternalSearchFailure.NETWORK), v.state.value.editions)

        search.editionsResult = Result.success(listOf(kioon))
        v.onRetryEditions()
        advanceUntilIdle()
        assertEquals(EditionsStep.Loaded(listOf(kioon)), v.state.value.editions)
    }

    @Test
    fun `le retour depuis les editions rend les candidats sans relancer la recherche`() = runTest {
        search.seriesSearchResult = Result.success(listOf(frieren))
        search.editionsResult = Result.success(listOf(kioon))
        val v = vm()
        v.onQueryChange("Frieren")
        advanceUntilIdle()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()

        assertTrue(v.onBack())
        assertEquals(SeriesSearchStage.SEARCH, v.state.value.stage)
        assertTrue(v.state.value.step is SeriesSearchStep.Results)
        assertEquals(1, search.seriesQueries.size)
        // À l'étape de recherche, le retour n'est plus consommé : l'écran se ferme.
        assertFalse(v.onBack())
    }

    // ---- Étape 3 : l'import -----------------------------------------------------------------

    @Test
    fun `choisir une edition lance le job puis cree tous les tomes en wishlist`() = runTest {
        search.editionsResult = Result.success(listOf(kioon))
        search.editionMappingResult = Result.success(mapping)
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()

        v.onSelectEdition(kioon)
        assertEquals(SeriesSearchStage.IMPORT, v.state.value.stage)
        assertTrue(v.state.value.importing)
        advanceUntilIdle()

        // Le job réchauffe exactement l'énumération que l'import relit : éditeur et id MangaDex compris.
        assertEquals("Frieren", search.lastImportJobTitle)
        assertEquals("Ki-oon", search.lastImportJobPublisher)
        assertEquals("md-frieren", search.lastImportJobMangaId)

        assertNotNull(v.state.value.result)
        val result = v.state.value.result!!
        assertEquals(ScanImportPhase.DONE, v.state.value.importPhase)
        assertFalse(v.state.value.importing)
        assertEquals(2, result.createdVolumes)
        // Série en cours : signalée partielle (la BnF catalogue les derniers tomes avec retard).
        assertTrue(result.partial)
        assertTrue(records.listAll().all { it.status == ItemStatus.WISHLIST })
        assertEquals("Ki-oon", series.listAll().single().publisherFr)
    }

    @Test
    fun `l'avancement du job est expose pendant le poll`() = runTest {
        search.editionsResult = Result.success(listOf(kioon))
        search.editionMappingResult = Result.success(mapping)
        search.startImportJobResult = Result.success(ImportJob(jobId = "j1", state = ImportJobState.RUNNING))
        search.importJobStatusSequence = listOf(
            Result.success(ImportJob(jobId = "j1", state = ImportJobState.RUNNING)),
            Result.success(ImportJob(jobId = "j1", state = ImportJobState.DONE))
        )
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()

        v.onSelectEdition(kioon)
        dispatcher.scheduler.runCurrent()
        assertEquals(ScanImportPhase.POLLING, v.state.value.importPhase)

        advanceUntilIdle()
        assertEquals(ScanImportPhase.DONE, v.state.value.importPhase)
        assertTrue(search.importJobStatusCallCount >= 2)
    }

    @Test
    fun `un double tap ne lance qu'un import et le retour est bloque pendant l'import`() = runTest {
        search.editionsResult = Result.success(listOf(kioon))
        search.editionMappingResult = Result.success(mapping)
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()

        v.onSelectEdition(kioon)
        v.onSelectEdition(kioon)
        assertTrue(v.onBack())
        assertEquals(SeriesSearchStage.IMPORT, v.state.value.stage)
        advanceUntilIdle()

        assertEquals(1, search.startImportJobCallCount)
        assertEquals(2, records.listAll().size)
    }

    @Test
    fun `une enumeration indisponible cree la serie vide et le dit`() = runTest {
        search.editionsResult = Result.success(listOf(kioon))
        // Mapping en échec (défaut du fake) : l'import dégrade au lieu d'échouer.
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()
        v.onSelectEdition(kioon)
        advanceUntilIdle()

        val result = v.state.value.result!!
        assertTrue(result.degradedFullImport)
        assertEquals(0, result.createdVolumes)
        assertEquals(1, series.listAll().size)
    }

    // ---- Revue : échec d'import et relance ----------------------------------------------------

    @Test
    fun `un import qui leve une exception relache l'ecran et permet de revenir`() = runTest {
        search.editionsResult = Result.success(listOf(kioon))
        search.editionMappingResult = Result.success(mapping)
        series.failOnUpsert = IllegalStateException("disque plein")
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()

        v.onSelectEdition(kioon)
        advanceUntilIdle()

        val state = v.state.value
        assertTrue(state.importFailed)
        assertFalse(state.importing)
        assertNull(state.result)
        // Retour aux éditions possible, plus de blocage.
        assertTrue(v.onBack())
        assertEquals(SeriesSearchStage.EDITIONS, v.state.value.stage)
        assertFalse(v.state.value.importFailed)
    }

    @Test
    fun `un import en echec se relance sur la meme edition`() = runTest {
        search.editionsResult = Result.success(listOf(kioon))
        search.editionMappingResult = Result.success(mapping)
        series.failOnUpsert = IllegalStateException("disque plein")
        val v = vm()
        v.onSelectCandidate(frieren)
        advanceUntilIdle()
        v.onSelectEdition(kioon)
        advanceUntilIdle()

        series.failOnUpsert = null
        v.onRetryImport()
        advanceUntilIdle()

        assertFalse(v.state.value.importFailed)
        assertEquals(2, v.state.value.result?.createdVolumes)
    }

    @Test
    fun `une relance lente ne remplace pas le resultat d'une recherche plus recente`() = runTest {
        val crossover = frieren.copy(sourceId = "md-x", mangaId = "md-x", title = "Frieren Cinnamoroll")
        search.seriesSearchHandler = { query ->
            if (query == "aaa") {
                kotlinx.coroutines.delay(5_000)
                Result.success(listOf(crossover))
            } else {
                Result.success(listOf(frieren))
            }
        }
        val v = vm()
        v.onQueryChange("aaa")
        advanceUntilIdle()

        // Relance lente de « aaa », puis l'utilisateur tape autre chose avant qu'elle n'aboutisse.
        v.onRetry()
        advanceTimeBy(10)
        v.onQueryChange("bbb")
        advanceUntilIdle()

        val step = v.state.value.step as SeriesSearchStep.Results
        assertEquals(listOf("md-frieren"), step.candidates.map { it.sourceId })
    }

    @Test
    fun `la relance passe par la recherche courante`() = runTest {
        search.seriesSearchResult = Result.failure(ExternalSearchException(ExternalSearchFailure.NETWORK))
        val v = vm()
        v.onQueryChange("Frieren")
        advanceUntilIdle()

        search.seriesSearchResult = Result.success(listOf(frieren))
        v.onRetry()
        advanceUntilIdle()

        assertTrue(v.state.value.step is SeriesSearchStep.Results)
        assertEquals(listOf("Frieren", "Frieren"), search.seriesQueries)
    }
}

private object NoopCovers : CoverStorage {
    override suspend fun saveFromUri(recordId: String, uriString: String): String = pathFor(recordId)
    override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = pathFor(recordId)
    override suspend fun saveFromUrl(recordId: String, url: String): String = pathFor(recordId)
    override suspend fun delete(recordId: String) = Unit
    override fun pathFor(recordId: String): String = "/fake/covers/$recordId.jpg"
}
