package fr.beudaert.acervatim.data.local.db

import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `collectionType roundtrip`() {
        val raw = converters.collectionTypeToString(CollectionType.MANGA)
        assertEquals("MANGA", raw)
        assertEquals(CollectionType.MANGA, converters.stringToCollectionType(raw))
    }

    @Test
    fun `itemStatus roundtrip covers IGNORED`() {
        val raw = converters.itemStatusToString(ItemStatus.IGNORED)
        assertEquals("IGNORED", raw)
        assertEquals(ItemStatus.IGNORED, converters.stringToItemStatus(raw))
    }

    @Test
    fun `extras roundtrip for empty map`() {
        val raw = converters.extrasToString(emptyMap())
        assertEquals("{}", raw)
        assertTrue(converters.stringToExtras(raw).isEmpty())
    }

    @Test
    fun `extras roundtrip for manga fields`() {
        val map = linkedMapOf(
            "isbn" to "9784088770734",
            "publisherFr" to "Glénat",
            "originalAuthor" to "Kentarō Miura",
            "demographic" to "SEINEN",
            "pageCount" to "236"
        )
        val raw = converters.extrasToString(map)
        val parsed = converters.stringToExtras(raw)
        assertEquals(map.size, parsed.size)
        assertEquals("9784088770734", parsed["isbn"])
        assertEquals("Glénat", parsed["publisherFr"])
        assertEquals("Kentarō Miura", parsed["originalAuthor"])
        assertEquals("SEINEN", parsed["demographic"])
        assertEquals("236", parsed["pageCount"])
    }

    @Test
    fun `extras parsing handles blank input`() {
        assertTrue(converters.stringToExtras("").isEmpty())
        assertTrue(converters.stringToExtras("  ").isEmpty())
    }

    @Test
    fun `extras escaping survives quotes in values`() {
        val map = mapOf("synopsisQuote" to "il a dit \"bonjour\"")
        val raw = converters.extrasToString(map)
        val parsed = converters.stringToExtras(raw)
        assertEquals("il a dit \"bonjour\"", parsed["synopsisQuote"])
    }

    @Test
    fun `roundtrip preserves null-status converter pair`() {
        assertNull(converters.epochMillisToInstant(null))
        assertNotNull(converters.instantToEpochMillis(java.time.Instant.now()))
    }
}
