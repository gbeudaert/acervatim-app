package app.acervatim.ui.search

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.FakeAuthRepository
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.usecase.SearchExternalByTextUseCase
import app.acervatim.ui.scan.ScanPrefillHandoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

class ExternalSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var search: FakeExternalSearchRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var handoff: ScanPrefillHandoff

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        search = FakeExternalSearchRepository()
        auth = FakeAuthRepository()
        auth.setSignedIn("user-1")
        handoff = ScanPrefillHandoff()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(collectionType: String? = "VINYL"): ExternalSearchViewModel =
        ExternalSearchViewModel(
            savedStateHandle = SavedStateHandle(
                buildMap { collectionType?.let { put("collectionType", it) } }
            ),
            searchExternal = SearchExternalByTextUseCase(search, auth),
            prefillHandoff = handoff
        )

    private fun pressing(
        sourceId: String,
        masterId: String? = null,
        year: Int? = null
    ) = ExternalMatch(
        source = "discogs",
        sourceId = sourceId,
        title = "Endless Smile",
        creators = listOf("Degiheugi"),
        year = year,
        masterId = masterId
    )

    @Test
    fun `l'etat de depart n'a rien a montrer et n'appelle personne`() = runTest {
        val v = vm()
        advanceUntilIdle()

        assertEquals(SearchStep.Idle, v.state.value.step)
        assertEquals(0, search.textCallCount)
    }

    @Test
    fun `une requete trop courte ne part pas sur le reseau`() = runTest {
        val v = vm()
        v.onQueryChange("de")
        advanceUntilIdle()

        assertEquals(SearchStep.Idle, v.state.value.step)
        assertEquals(0, search.textCallCount)
    }

    @Test
    fun `une saisie tapee caractere par caractere ne declenche qu'une requete`() = runTest {
        // Le point du debounce : le quota Discogs est consommé quasiment au rapport 1:1 en recherche
        // texte, une requête par frappe le viderait sur des préfixes de mots inachevés.
        val v = vm()
        listOf("d", "de", "deg", "degi", "degih").forEach { typed ->
            v.onQueryChange(typed)
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(1, search.textCallCount)
        assertEquals("degih", search.textQueries.single())
        assertEquals("vinyl", search.lastTextType)
    }

    @Test
    fun `deux saisies distinctes espacees donnent deux requetes`() = runTest {
        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()
        v.onQueryChange("bagatelle")
        advanceUntilIdle()

        assertEquals(listOf("degiheugi", "bagatelle"), search.textQueries)
    }

    @Test
    fun `une saisie qui retombe sur le meme texte ne relance pas la recherche`() = runTest {
        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()
        v.onQueryChange("degiheugix")
        v.onQueryChange("degiheugi")
        advanceUntilIdle()

        assertEquals(1, search.textCallCount)
    }

    @Test
    fun `les resultats sortent regroupes par album`() = runTest {
        search.textResult = Result.success(
            listOf(
                pressing("1", masterId = "42", year = 2021),
                pressing("2", masterId = "42", year = 2017),
                pressing("3", masterId = "99", year = 2010)
            )
        )

        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()

        val step = v.state.value.step
        assertTrue(step is SearchStep.Results)
        val groups = (step as SearchStep.Results).groups
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].pressings.size)
        assertTrue(groups[1].isSingle)
    }

    @Test
    fun `aucun resultat est un etat distinct`() = runTest {
        search.textResult = Result.success(emptyList())

        val v = vm()
        v.onQueryChange("xyzzy")
        advanceUntilIdle()

        assertEquals(SearchStep.NoMatch, v.state.value.step)
    }

    @Test
    fun `un refus de jeton ne s'affiche pas comme aucun resultat`() = runTest {
        // Le contresens à éviter : l'utilisateur croirait que son disque n'existe pas.
        search.textResult = Result.failure(
            ExternalSearchException(ExternalSearchFailure.SOURCE_TOKEN_REQUIRED, provider = "discogs")
        )

        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()

        val step = v.state.value.step
        assertTrue(step is SearchStep.Failed)
        assertEquals(ExternalSearchFailure.SOURCE_TOKEN_REQUIRED, (step as SearchStep.Failed).failure)
        assertEquals("discogs", step.provider)
    }

    @Test
    fun `une erreur reseau est son propre etat`() = runTest {
        search.textResult = Result.failure(
            ExternalSearchException(ExternalSearchFailure.NETWORK, cause = IOException("offline"))
        )

        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()

        assertEquals(
            ExternalSearchFailure.NETWORK,
            (v.state.value.step as SearchStep.Failed).failure
        )
    }

    @Test
    fun `hors session la recherche ne part pas sur le reseau`() = runTest {
        auth.signOut()

        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()

        assertEquals(SearchStep.NotConnected, v.state.value.step)
        assertEquals(0, search.textCallCount)
    }

    @Test
    fun `reessayer relance la requete courante sans attendre le debounce`() = runTest {
        search.textResult = Result.failure(ExternalSearchException(ExternalSearchFailure.NETWORK))

        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()
        assertEquals(1, search.textCallCount)

        search.textResult = Result.success(listOf(pressing("1", masterId = "42")))
        v.onRetry()
        advanceUntilIdle()

        assertEquals(2, search.textCallCount)
        assertTrue(v.state.value.step is SearchStep.Results)
    }

    @Test
    fun `choisir un pressage le depose au passe-plat sous sa cle`() = runTest {
        val match = pressing("r123", masterId = "42", year = 2017)
        search.textResult = Result.success(listOf(match))

        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()
        v.onSelectPressing(match)

        val event = v.events.value
        assertTrue(event is ExternalSearchEvent.PrefillNewRecord)
        val key = (event as ExternalSearchEvent.PrefillNewRecord).prefillKey
        // La clé n'est pas le code-barres : un disque trouvé par titre n'en a pas forcément.
        assertEquals("discogs:r123", key)
        assertEquals(match, handoff.take(key))
    }

    @Test
    fun `deplier puis replier un album`() = runTest {
        val v = vm()
        v.onToggleGroup("master:42")
        assertEquals(setOf("master:42"), v.state.value.expandedGroups)
        v.onToggleGroup("master:42")
        assertTrue(v.state.value.expandedGroups.isEmpty())
    }

    @Test
    fun `une nouvelle recherche repart repliee`() = runTest {
        search.textResult = Result.success(listOf(pressing("1", masterId = "42")))

        val v = vm()
        v.onQueryChange("degiheugi")
        advanceUntilIdle()
        v.onToggleGroup("master:42")

        v.onQueryChange("bagatelle")
        advanceUntilIdle()

        assertTrue(v.state.value.expandedGroups.isEmpty())
    }

    @Test
    fun `l'evenement se consomme une seule fois`() = runTest {
        val v = vm()
        v.onSelectPressing(pressing("r123"))
        assertTrue(v.events.value is ExternalSearchEvent.PrefillNewRecord)
        v.onEventConsumed()
        assertNull(v.events.value)
    }
}
