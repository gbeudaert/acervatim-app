package app.acervatim.domain.usecase

import app.acervatim.domain.FakeAuthRepository
import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.model.ExternalMatch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LookupByBarcodeUseCaseTest {

    private val search = FakeExternalSearchRepository()
    private val auth = FakeAuthRepository()
    private val useCase = LookupByBarcodeUseCase(search, auth)

    private val match = ExternalMatch(source = "discogs", sourceId = "1", title = "Album")

    @Test
    fun `blank barcode short-circuits to NoMatch`() = runTest {
        auth.setSignedIn("user-1")
        val result = useCase("   ", "vinyl")
        assertEquals(LookupResult.NoMatch, result)
        assertEquals(0, search.callCount)
    }

    @Test
    fun `signed out short-circuits to NotConnected without network`() = runTest {
        val result = useCase("123", "vinyl")
        assertEquals(LookupResult.NotConnected, result)
        assertEquals(0, search.callCount)
    }

    @Test
    fun `empty results map to NoMatch`() = runTest {
        auth.setSignedIn("user-1")
        search.result = Result.success(emptyList())
        assertEquals(LookupResult.NoMatch, useCase("123", "vinyl"))
        assertEquals("123", search.lastBarcode)
        assertEquals("vinyl", search.lastType)
    }

    @Test
    fun `non-empty results map to Matches`() = runTest {
        auth.setSignedIn("user-1")
        search.result = Result.success(listOf(match))
        val result = useCase("123", "manga")
        assertTrue(result is LookupResult.Matches)
        assertEquals(listOf(match), (result as LookupResult.Matches).matches)
    }

    @Test
    fun `repository failure maps to Error`() = runTest {
        auth.setSignedIn("user-1")
        val boom = IOException("offline")
        search.result = Result.failure(boom)
        val result = useCase("123", "vinyl")
        assertTrue(result is LookupResult.Error)
        assertEquals(boom, (result as LookupResult.Error).cause)
    }
}
