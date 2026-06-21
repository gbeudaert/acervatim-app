package app.acervatim.data.remote.mapper

import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordingSpeed
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FacadeMapperTest {

    private val now: Instant = Instant.parse("2026-06-09T10:00:00Z")

    private fun vinylRecord(): Record = Record(
        id = "rec-1",
        collectionType = CollectionType.VINYL,
        artists = listOf(Artist("a1", "Miles Davis"), Artist("a2", "John Coltrane")),
        title = "Kind of Blue",
        genre = "Jazz",
        year = 1959,
        label = "Columbia",
        format = AcervatimFormat.LP_33,
        recordingSpeed = RecordingSpeed.RPM_33,
        country = "US",
        purchasePrice = 25.0,
        rating = 5,
        coverRemoteUrl = "https://img/kob.jpg",
        barcode = "1234567890123",
        discogsId = 999L,
        status = ItemStatus.OWNED,
        lastPlayedAt = now,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `vinyl unifiedData carries the full profile fields`() {
        val facade = FacadeMapper.recordToRemote(vinylRecord(), collectionLocalId = "col-1", nodeLocalId = null)
        val unified = facade.item.unifiedData

        assertEquals("Kind of Blue", unified.string("title"))
        assertEquals(listOf("Miles Davis", "John Coltrane"), unified.stringList("creators"))
        assertEquals(listOf("Jazz"), unified.stringList("genre"))
        assertEquals("Columbia", unified.string("label"))
        assertTrue(unified.string("releaseDate")?.startsWith("1959") == true)
    }

    @Test
    fun `barcode isbn discogs map to sources`() {
        val facade = FacadeMapper.recordToRemote(vinylRecord(), "col-1", null)
        val providers = facade.item.sources.map { it.provider }
        assertTrue(providers.contains(FacadeMapper.PROVIDER_BARCODE))
        assertTrue(providers.contains(FacadeMapper.PROVIDER_DISCOGS))
    }

    @Test
    fun `app-only fields go to localExtras not unifiedData`() {
        val facade = FacadeMapper.recordToRemote(vinylRecord(), "col-1", null)
        // rating / prix / vitesse / pays / dernière écoute / statut n'ont pas de home dans le contrat V1.
        assertEquals(5, facade.localExtras.string("rating")?.toInt())
        assertEquals("US", facade.localExtras.string("country"))
        assertTrue(facade.item.unifiedData["rating"] == null)
    }

    @Test
    fun `round-trip Record - RemoteItem - Record preserves modelled and app-only fields`() {
        val original = vinylRecord()
        val facade = FacadeMapper.recordToRemote(original, "col-1", null)
        val restored = FacadeMapper.remoteToRecord(
            item = facade.item,
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = facade.localExtras,
            createdAt = original.createdAt
        )

        assertEquals(original.title, restored.title)
        assertEquals(original.genre, restored.genre)
        assertEquals(original.year, restored.year)
        assertEquals(original.label, restored.label)
        assertEquals(original.format, restored.format)
        assertEquals(original.country, restored.country)
        assertEquals(original.rating, restored.rating)
        assertEquals(original.recordingSpeed, restored.recordingSpeed)
        assertEquals(original.barcode, restored.barcode)
        assertEquals(original.discogsId, restored.discogsId)
        assertEquals(original.status, restored.status)
        assertEquals(original.artists.map { it.name }, restored.artists.map { it.name })
    }

    // Réplique des helpers privés du mapper, pour lire le JsonObject dans le test.
    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let {
            if (it.isString) it.content else it.content
        }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        } ?: emptyList()
}
