package app.acervatim.data.remote.search

import app.acervatim.data.remote.dto.ProblemDetailsDto
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.data.remote.error.ApiException
import app.acervatim.data.remote.sync.FakeAcervatimApi
import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le chemin texte de `GET /v1/search` : `q` **XOR** `barcode` (le backend rejette les deux), et
 * traduction des erreurs de transport en causes que l'UI sait dire. Le cas qui compte est le
 * `403 source-token-required` : le rendre en liste vide ferait croire à l'utilisateur que son disque
 * n'est pas référencé.
 */
class ExternalSearchByTextTest {

    private val api = FakeAcervatimApi()
    private val repository = ExternalSearchRepositoryImpl(api)

    private fun item(sourceId: String) = UnifiedItemDto(
        source = "discogs",
        sourceId = sourceId,
        mediaType = "vinyl",
        title = "Degiheugi - Endless Smile"
    )

    @Test
    fun `la recherche texte envoie q et jamais barcode`() = runTest {
        api.searchResults = listOf(item("1"))

        val result = repository.searchByText("degiheugi", "vinyl")

        assertTrue(result.isSuccess)
        val call = api.searchCalls.single()
        assertEquals("vinyl", call.type)
        assertEquals("degiheugi", call.query)
        assertNull(call.barcode)
    }

    @Test
    fun `la recherche par code-barres envoie barcode et jamais q`() = runTest {
        api.searchResults = listOf(item("1"))

        repository.searchByBarcode("3596971234567", "vinyl")

        val call = api.searchCalls.single()
        assertEquals("3596971234567", call.barcode)
        assertNull(call.query)
    }

    @Test
    fun `403 source-token-required devient SOURCE_TOKEN_REQUIRED et nomme le provider`() = runTest {
        api.searchError = ApiException.Forbidden(
            ProblemDetailsDto(
                type = "https://api.acervatim/probs/source-token-required",
                status = 403,
                provider = "discogs"
            )
        )

        val failure = repository.searchByText("degiheugi", "vinyl").exceptionOrNull()

        assertTrue(failure is ExternalSearchException)
        failure as ExternalSearchException
        assertEquals(ExternalSearchFailure.SOURCE_TOKEN_REQUIRED, failure.failure)
        assertEquals("discogs", failure.provider)
    }

    @Test
    fun `un 403 ordinaire ne se fait pas passer pour un refus de jeton`() = runTest {
        api.searchError = ApiException.Forbidden(
            ProblemDetailsDto(type = "https://api.acervatim/probs/forbidden", status = 403)
        )

        val failure = repository.searchByText("degiheugi", "vinyl").exceptionOrNull()

        assertEquals(
            ExternalSearchFailure.UNKNOWN,
            (failure as ExternalSearchException).failure
        )
    }

    @Test
    fun `une source indisponible est traitee comme une panne reseau`() = runTest {
        // 502/503 : la source externe (ou l'attente en file) a lâché — c'est passager, donc
        // retentable, au même titre qu'une coupure réseau.
        api.searchError = ApiException.Upstream(502, null)

        val failure = repository.searchByText("degiheugi", "vinyl").exceptionOrNull()

        assertEquals(
            ExternalSearchFailure.NETWORK,
            (failure as ExternalSearchException).failure
        )
    }

    @Test
    fun `un 429 devient RATE_LIMITED`() = runTest {
        api.searchError = ApiException.TooManyRequests(null)

        val failure = repository.searchByText("degiheugi", "vinyl").exceptionOrNull()

        assertEquals(
            ExternalSearchFailure.RATE_LIMITED,
            (failure as ExternalSearchException).failure
        )
    }

    @Test
    fun `une coupure reseau devient NETWORK`() = runTest {
        api.searchError = java.io.IOException("offline")

        val failure = repository.searchByText("degiheugi", "vinyl").exceptionOrNull()

        assertEquals(
            ExternalSearchFailure.NETWORK,
            (failure as ExternalSearchException).failure
        )
    }

    @Test
    fun `aucun resultat reste un succes, pas une erreur`() = runTest {
        api.searchResults = emptyList()

        val result = repository.searchByText("xyzzy", "vinyl")

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrThrow())
    }
}
