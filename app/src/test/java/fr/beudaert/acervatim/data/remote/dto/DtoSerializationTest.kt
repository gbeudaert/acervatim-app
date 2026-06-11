package fr.beudaert.acervatim.data.remote.dto

import fr.beudaert.acervatim.data.remote.error.ApiException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun `decodes paginated collection envelope`() {
        val raw = """
            {
              "data": [
                {"id":"c1","userId":"u1","type":"vinyl","name":"Mes vinyles","itemCount":3,
                 "createdAt":"2026-06-08T20:19:48.366Z","updatedAt":"2026-06-08T20:19:48.366Z"}
              ],
              "meta": {"pagination": {"nextCursor":"abc","limit":50}}
            }
        """.trimIndent()

        val page = json.decodeFromString(PageDto.serializer(CollectionDto.serializer()), raw)

        assertEquals(1, page.data.size)
        assertEquals("vinyl", page.data.first().type)
        assertEquals("abc", page.meta.pagination.nextCursor)
    }

    @Test
    fun `nextCursor null marks last page`() {
        val raw = """{"data":[],"meta":{"pagination":{"nextCursor":null,"limit":50}}}"""
        val page = json.decodeFromString(PageDto.serializer(CollectionDto.serializer()), raw)
        assertNull(page.meta.pagination.nextCursor)
    }

    @Test
    fun `problem details exposes type slug and field errors`() {
        val raw = """
            {
              "type":"https://api.acervatim/probs/validation-error",
              "title":"Invalid request payload","status":400,
              "detail":"name: too long","instance":"/v1/collections","requestId":"req-1",
              "errors":[{"field":"name","code":"too_big","message":"max 255"}]
            }
        """.trimIndent()

        val problem = json.decodeFromString(ProblemDetailsDto.serializer(), raw)

        assertEquals("validation-error", problem.typeSlug)
        assertEquals("req-1", problem.requestId)
        assertEquals(1, problem.errors?.size)
    }

    @Test
    fun `quota max null means unlimited`() {
        val raw = """{"collections":{"used":3,"max":null},"items":{"used":42,"max":null}}"""
        val quota = json.decodeFromString(QuotaResponse.serializer(), raw)
        assertNull(quota.collections.max)
        assertNull(quota.items.max)
    }

    @Test
    fun `vinyl unifiedData omits null and default fields on encode (strict-friendly)`() {
        val dto = VinylUnifiedDataDto(title = "Kind of Blue")
        val encoded = json.encodeToString(VinylUnifiedDataDto.serializer(), dto)
        // Pas de label/format/releaseDate null, pas de creators/genre vides → corps minimal.
        assertTrue(encoded.contains("\"title\""))
        assertTrue(!encoded.contains("label"))
        assertTrue(!encoded.contains("creators"))
    }

    @Test
    fun `ApiException maps status codes to typed subclasses`() {
        assertTrue(ApiException.from(401, null) is ApiException.Unauthorized)
        assertTrue(ApiException.from(402, ProblemDetailsDto(type = "x/quota-exceeded")) is ApiException.QuotaExceeded)
        assertTrue(ApiException.from(402, ProblemDetailsDto(type = "x/payment-required")) is ApiException.PaymentRequired)
        assertTrue(ApiException.from(409, null) is ApiException.Conflict)
        assertTrue(ApiException.from(404, null) is ApiException.NotFound)
        assertTrue(ApiException.from(400, ProblemDetailsDto(type = "x/bad-request")) is ApiException.BadRequest)
        assertTrue(ApiException.from(400, ProblemDetailsDto(type = "x/validation-error")) is ApiException.Validation)
        assertTrue(ApiException.from(502, null) is ApiException.Upstream)
        assertTrue(ApiException.from(500, null) is ApiException.Generic)
    }

    @Test
    fun `source list with raw decodes`() {
        val raw = """[{"provider":"mal","externalId":"7","rawData":{"k":1},"fetchedAt":null}]"""
        val sources = json.decodeFromString(ListSerializer(SourceWithRawDto.serializer()), raw)
        assertEquals("mal", sources.first().provider)
    }
}
