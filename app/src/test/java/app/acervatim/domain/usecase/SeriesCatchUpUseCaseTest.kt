package app.acervatim.domain.usecase

import app.acervatim.domain.FakeAuthRepository
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesCatchUpStore
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesCatchUpState
import app.acervatim.domain.model.SeriesUpdate
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.account.PremiumSource
import app.acervatim.domain.model.account.PremiumStatus
import app.acervatim.domain.model.account.UserProfile
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.ui.sharing.FakeAccountRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SeriesCatchUpUseCaseTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 11, 2)
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var records: FakeRecordRepository
    private lateinit var series: FakeSeriesRepository
    private lateinit var search: FakeExternalSearchRepository
    private lateinit var store: FakeSeriesCatchUpStore
    private lateinit var auth: FakeAuthRepository
    private lateinit var account: FakeAccountRepository
    private lateinit var useCase: SeriesCatchUpUseCase
    private lateinit var updatesCache: SeriesUpdatesCache

    @Before
    fun setUp() {
        records = FakeRecordRepository()
        series = FakeSeriesRepository(records)
        search = FakeExternalSearchRepository()
        store = FakeSeriesCatchUpStore()
        // État déjà rattaché à l'utilisateur connecté : sinon la première vérification le vide.
        store.currentOwner = "u1"
        auth = FakeAuthRepository(initialUserId = "u1")
        account = FakeAccountRepository()
        updatesCache = SeriesUpdatesCache(search, clock)
        useCase = SeriesCatchUpUseCase(
            seriesRepository = series,
            recordRepository = records,
            externalSearch = search,
            store = store,
            authRepository = auth,
            accountRepository = account,
            importSeries = ImportScannedSeriesUseCase(series, records, search, NoopCoverStorage, clock),
            updatesCache = updatesCache,
            clock = clock
        )
    }

    private object NoopCoverStorage : CoverStorage {
        override suspend fun saveFromUri(recordId: String, uriString: String): String = ""
        override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = ""
        override suspend fun saveFromUrl(recordId: String, url: String): String = "local/$recordId"
        override suspend fun delete(recordId: String) = Unit
        override fun pathFor(recordId: String): String = ""
    }

    /** Série suivie, créée il y a 30 jours (première vérification due). */
    private suspend fun seedSeries(
        publisherFr: String? = "Ki-oon",
        status: String? = "En cours",
        createdAt: Instant = now.minusSeconds(30L * 86_400)
    ): Series {
        val s = Series(
            id = "s1",
            collectionType = CollectionType.MANGA,
            title = "Frieren",
            originalAuthor = "Kanehito Yamada",
            publicationStatus = status,
            publisherFr = publisherFr,
            mangaId = "b0b721ff-c388-4486-aa0f-c2b0bb321512",
            createdAt = createdAt,
            updatedAt = createdAt
        )
        series.upsert(s)
        return s
    }

    private suspend fun seedVolume(volume: Int, isbn: String?) {
        records.upsert(
            Record(
                id = "r$volume",
                collectionType = CollectionType.MANGA,
                seriesId = "s1",
                volumeNumber = volume,
                artists = emptyList(),
                title = "Frieren. $volume",
                isbn = isbn,
                status = ItemStatus.OWNED,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun tome(volume: Int, isbn: String?, cover: String? = "https://c/$volume.jpg") =
        EditionTome(editionVolume = volume, isbn = isbn, titleFr = "Frieren. $volume", coverUrl = cover)

    private fun mapping(vararg tomes: EditionTome, complete: Boolean = true) = Result.success(
        EditionMapping(
            titleFr = "Frieren",
            edition = null,
            tomeCount = tomes.size,
            tomes = tomes.toList(),
            ongoing = true,
            publisher = "Ki-oon",
            complete = complete
        )
    )

    private fun premium() {
        account.info.value = AccountInfo.Known(
            UserProfile(
                userId = "u1",
                createdAt = now,
                premium = PremiumStatus(isPremium = true, source = PremiumSource.SUBSCRIPTION, expiresAt = null)
            )
        )
    }

    // ---- Garde-fous : aucun appel réseau -------------------------------------------------------

    @Test
    fun `hors session - aucun appel reseau, rien a proposer`() = runTest {
        seedSeries()
        auth.signOut()

        assertTrue(useCase.check("s1").isEmpty())
        assertEquals(0, search.editionMappingCallCount)
    }

    @Test
    fun `serie sans editeur ou terminee - aucun appel`() = runTest {
        seedSeries(publisherFr = null)
        assertTrue(useCase.check("s1").isEmpty())
        seedSeries(status = "Terminée")
        assertTrue(useCase.check("s1").isEmpty())
        assertEquals(0, search.editionMappingCallCount)
    }

    @Test
    fun `verifiee il y a moins de 7 jours - aucun appel, l'attente est servie du stockage`() = runTest {
        seedSeries()
        store.states["s1"] = SeriesCatchUpState(
            lastCheckedOn = today.minusDays(3),
            pending = listOf(tome(15, "9791032711111"))
        )

        val proposed = useCase.check("s1")

        assertEquals(0, search.editionMappingCallCount)
        assertEquals(listOf(15), proposed.map { it.editionVolume })
    }

    // ---- Vérification --------------------------------------------------------------------------

    @Test
    fun `verification due - since = reference moins 7 jours, parametres de l'edition, tomes absents seulement`() = runTest {
        seedSeries()
        seedVolume(13, "9791032700013")
        seedVolume(14, null)
        store.states["s1"] = SeriesCatchUpState(lastCheckedOn = today.minusDays(10))
        search.editionMappingResult = mapping(
            tome(13, "9791032700013"),
            tome(14, "9791032700014"),
            tome(15, "9791032700015")
        )

        val proposed = useCase.check("s1")

        assertEquals(today.minusDays(17), search.lastEditionSince)
        assertEquals("Frieren", search.lastEditionTitle)
        assertEquals("Ki-oon", search.lastEditionPublisher)
        assertEquals(listOf(15), proposed.map { it.editionVolume })
        assertEquals(today, store.states.getValue("s1").lastCheckedOn)
    }

    @Test
    fun `premiere verification - since part de la creation de la serie`() = runTest {
        seedSeries(createdAt = Instant.parse("2026-10-01T22:00:00Z"))
        search.editionMappingResult = mapping()

        useCase.check("s1")

        assertEquals(LocalDate.of(2026, 9, 24), search.lastEditionSince)
    }

    @Test
    fun `enumeration incomplete - tomes gardes mais date non avancee`() = runTest {
        seedSeries()
        store.states["s1"] = SeriesCatchUpState(lastCheckedOn = today.minusDays(10))
        search.editionMappingResult = mapping(tome(15, "9791032700015"), complete = false)

        val proposed = useCase.check("s1")

        assertEquals(listOf(15), proposed.map { it.editionVolume })
        assertEquals(today.minusDays(10), store.states.getValue("s1").lastCheckedOn)
    }

    @Test
    fun `echec reseau - silencieux, date non avancee, attente conservee`() = runTest {
        seedSeries()
        store.states["s1"] = SeriesCatchUpState(
            lastCheckedOn = today.minusDays(10),
            pending = listOf(tome(15, "9791032700015"))
        )
        search.editionMappingResult = Result.failure(IOException("offline"))

        val proposed = useCase.check("s1")

        assertEquals(listOf(15), proposed.map { it.editionVolume })
        assertEquals(today.minusDays(10), store.states.getValue("s1").lastCheckedOn)
    }

    // ---- Premium -------------------------------------------------------------------------------

    @Test
    fun `premium - nouveautes serveur du noeud de la serie fusionnees et dedoublonnees`() = runTest {
        seedSeries()
        series.remoteIds["node-1"] = "s1"
        premium()
        search.editionMappingResult = mapping(tome(15, "9791032700015"))
        search.seriesUpdatesResult = Result.success(
            listOf(
                SeriesUpdate("node-other", "Autre", null, "Pika", listOf(tome(40, "9782811600040"))),
                SeriesUpdate(
                    "node-1", "Frieren", null, "Ki-oon",
                    listOf(tome(15, "9791032700015"), tome(16, "9791032700016"))
                )
            )
        )

        val proposed = useCase.check("s1")

        assertEquals(1, search.seriesUpdatesCallCount)
        assertEquals(listOf(15, 16), proposed.map { it.editionVolume })
    }

    @Test
    fun `premium - une seule lecture du remoteId et un seul telechargement pour deux series`() = runTest {
        seedSeries()
        series.remoteIds["node-1"] = "s1"
        premium()
        search.editionMappingResult = mapping()
        search.seriesUpdatesResult = Result.success(
            listOf(SeriesUpdate("node-1", "Frieren", null, "Ki-oon", listOf(tome(16, "9791032700016"))))
        )

        useCase.check("s1")
        assertEquals(1, series.remoteIdOfCalls)

        // Une autre série vérifiée dans la même session : la réponse du compte est déjà en mémoire.
        series.upsert(series.listAll().single().copy(id = "s2", title = "Autre série"))
        series.remoteIds["node-2"] = "s2"
        useCase.check("s2")

        assertEquals(1, search.seriesUpdatesCallCount)
    }

    @Test
    fun `premium - une serie jamais synchronisee ne telecharge rien`() = runTest {
        seedSeries()
        premium()
        search.editionMappingResult = mapping()

        useCase.check("s1")

        assertEquals(0, search.seriesUpdatesCallCount)
    }

    @Test
    fun `changement de compte - l'etat de rattrapage du compte precedent est oublie`() = runTest {
        seedSeries()
        store.currentOwner = "autre-compte"
        store.states["s-ancien"] = SeriesCatchUpState(pending = listOf(tome(3, "isbn-3")))
        search.editionMappingResult = mapping()

        useCase.check("s1")

        assertEquals("u1", store.currentOwner)
        assertNull(store.states["s-ancien"])
    }

    @Test
    fun `hors premium - series-updates n'est pas appele`() = runTest {
        seedSeries()
        search.editionMappingResult = mapping()

        useCase.check("s1")

        assertEquals(0, search.seriesUpdatesCallCount)
    }

    // ---- Plus tard / ajout -----------------------------------------------------------------------

    @Test
    fun `plus tard masque jusqu'a la verification reussie suivante`() = runTest {
        seedSeries()
        store.states["s1"] = SeriesCatchUpState(
            lastCheckedOn = today.minusDays(2),
            pending = listOf(tome(15, "9791032700015"))
        )

        useCase.dismiss("s1")
        assertTrue(useCase.check("s1").isEmpty())

        // Une semaine plus tard, la vérification réussit : la bannière revient, tome écarté compris.
        store.states["s1"] = store.states.getValue("s1").copy(lastCheckedOn = today.minusDays(8))
        search.editionMappingResult = mapping(tome(16, "9791032700016"))
        val proposed = useCase.check("s1")

        assertEquals(listOf(15, 16), proposed.map { it.editionVolume })
        assertNull(store.states.getValue("s1").dismissedOn)
    }

    @Test
    fun `ajout - tomes crees en wishlist avec isbn, titre, jaquette et edition, attente videe`() = runTest {
        seedSeries()
        seedVolume(14, "9791032700014")
        store.states["s1"] = SeriesCatchUpState(
            lastCheckedOn = today.minusDays(1),
            pending = listOf(tome(14, "9791032700014"), tome(15, "9791032700015"))
        )

        val result = useCase.addToWishlist("s1")

        assertNotNull(result)
        assertEquals(1, result!!.createdVolumes)
        val created = records.observeBySeriesId("s1").first().single { it.volumeNumber == 15 }
        assertEquals(ItemStatus.WISHLIST, created.status)
        assertEquals("9791032700015", created.isbn)
        assertEquals("Frieren. 15", created.title)
        assertEquals("https://c/15.jpg", created.coverRemoteUrl)
        assertEquals("Ki-oon", created.extras[MangaExtras.KEY_PUBLISHER_FR])
        assertTrue(store.states.getValue("s1").pending.isEmpty())
    }
}
