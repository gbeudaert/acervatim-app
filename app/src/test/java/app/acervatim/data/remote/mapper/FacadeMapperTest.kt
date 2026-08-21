package app.acervatim.data.remote.mapper

import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.data.remote.dto.PatchItemRequest
import app.acervatim.di.NetworkModule
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.model.sync.RemoteItem
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
        // rating / prix / vitesse / pays / dernière écoute n'ont pas de home dans le contrat V1.
        assertEquals(5, facade.localExtras.string("rating")?.toInt())
        assertEquals("US", facade.localExtras.string("country"))
        assertTrue(facade.item.unifiedData["rating"] == null)
    }

    @Test
    fun `status is pushed via userData not unifiedData`() {
        val facade = FacadeMapper.recordToRemote(
            vinylRecord().copy(status = ItemStatus.WISHLIST), "col-1", null
        )
        assertEquals("WISHLIST", facade.item.userData.string("status"))
        assertTrue(facade.item.unifiedData["status"] == null)
    }

    @Test
    fun `status stays in localExtras as the local snapshot for pulls`() {
        // localExtras est le cliché réinjecté au pull (preservedExtras) : sans statut dedans, un
        // item synchronisé avant l'ouverture du canal userData retomberait sur OWNED.
        val facade = FacadeMapper.recordToRemote(
            vinylRecord().copy(status = ItemStatus.IGNORED), "col-1", null
        )
        assertEquals("IGNORED", facade.localExtras.string("status"))
    }

    @Test
    fun `remote status wins over the local snapshot`() {
        val restored = FacadeMapper.remoteToRecord(
            item = remoteItemWithUserData(status = "WISHLIST"),
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = buildJsonObject { put("status", "OWNED") },
            createdAt = now
        )
        assertEquals(ItemStatus.WISHLIST, restored.status)
    }

    @Test
    fun `status falls back to the local snapshot when the server has none`() {
        // Cas d'un item synchronisé avant ce sprint : pas de statut distant, mais un statut local.
        val restored = FacadeMapper.remoteToRecord(
            item = remoteItemWithUserData(status = null),
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = buildJsonObject { put("status", "WISHLIST") },
            createdAt = now
        )
        assertEquals(ItemStatus.WISHLIST, restored.status)
    }

    @Test
    fun `status defaults to OWNED when neither side has one`() {
        val restored = FacadeMapper.remoteToRecord(
            item = remoteItemWithUserData(status = null),
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = JsonObject(emptyMap()),
            createdAt = now
        )
        assertEquals(ItemStatus.OWNED, restored.status)
    }

    @Test
    fun `an unknown remote status falls back instead of crashing`() {
        val restored = FacadeMapper.remoteToRecord(
            item = remoteItemWithUserData(status = "SOMETHING_ELSE"),
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = buildJsonObject { put("status", "IGNORED") },
            createdAt = now
        )
        assertEquals(ItemStatus.IGNORED, restored.status)
    }

    @Test
    fun `round-trip preserves a non-default status without any local snapshot`() {
        val original = vinylRecord().copy(status = ItemStatus.WISHLIST)
        val facade = FacadeMapper.recordToRemote(original, "col-1", null)
        val restored = FacadeMapper.remoteToRecord(
            item = facade.item,
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = JsonObject(emptyMap()),
            createdAt = original.createdAt
        )
        assertEquals(ItemStatus.WISHLIST, restored.status)
    }

    // ---- A1bis : synchronisation complète -------------------------------------------------

    @Test
    fun `vinyl unifiedData carries recordingSpeed and country`() {
        val unified = FacadeMapper.recordToRemote(vinylRecord(), "col-1", null).item.unifiedData
        assertEquals("RPM_33", unified.string("recordingSpeed"))
        assertEquals("US", unified.string("country"))
    }

    @Test
    fun `userData carries rating purchasePrice and lastPlayedAt alongside status`() {
        val userData = FacadeMapper.recordToRemote(vinylRecord(), "col-1", null).item.userData
        assertEquals("OWNED", userData.string("status"))
        assertEquals(5, userData["rating"]!!.jsonPrimitive.int)
        assertEquals(25.0, userData["purchasePrice"]!!.jsonPrimitive.double, 0.0)
        assertEquals(now.toString(), userData.string("lastPlayedAt"))
    }

    @Test
    fun `userData sends explicit nulls so clearing a field propagates`() {
        // Le userData est mergé côté serveur : omettre la clé laisserait l'ancienne valeur en place.
        val bare = vinylRecord().copy(rating = null, purchasePrice = null, lastPlayedAt = null)
        val userData = FacadeMapper.recordToRemote(bare, "col-1", null).item.userData

        assertEquals(JsonNull, userData["rating"])
        assertEquals(JsonNull, userData["purchasePrice"])
        assertEquals(JsonNull, userData["lastPlayedAt"])
    }

    @Test
    fun `userData never carries the server-side note field`() {
        // L'app ne modélise pas `note` : le merge PATCH ne doit jamais l'écraser.
        val userData = FacadeMapper.recordToRemote(vinylRecord(), "col-1", null).item.userData
        assertEquals(setOf("status", "rating", "purchasePrice", "lastPlayedAt"), userData.keys)
    }

    @Test
    fun `a residual rating of zero is sent as null rather than rejected`() {
        // Le schéma serveur borne rating à 1..5 : un 0 hérité ferait échouer le push en 400.
        val userData = FacadeMapper.recordToRemote(vinylRecord().copy(rating = 0), "col-1", null).item.userData
        assertEquals(JsonNull, userData["rating"])
    }

    @Test
    fun `explicit nulls survive serialization with the network Json config`() {
        // `explicitNulls = false` ne s'applique qu'aux propriétés de classes @Serializable, pas au
        // contenu d'un JsonObject — ce test verrouille ce comportement, dont dépend l'effacement.
        val bare = vinylRecord().copy(rating = null, purchasePrice = null, lastPlayedAt = null)
        val facade = FacadeMapper.recordToRemote(bare, "col-1", null)
        val body = RemoteDtoMapper.run { facade.item.toPatchRequest() }

        val encoded = NetworkModule.provideJson().encodeToString(PatchItemRequest.serializer(), body)

        assertTrue(encoded, encoded.contains("\"rating\":null"))
        assertTrue(encoded, encoded.contains("\"purchasePrice\":null"))
    }

    @Test
    fun `full round-trip loses nothing when the server holds everything`() {
        val original = vinylRecord()
        val facade = FacadeMapper.recordToRemote(original, "col-1", null)
        // Aucun cliché local : tout doit venir du serveur.
        val restored = FacadeMapper.remoteToRecord(
            item = facade.item,
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = JsonObject(emptyMap()),
            createdAt = original.createdAt
        )

        assertEquals(original.recordingSpeed, restored.recordingSpeed)
        assertEquals(original.country, restored.country)
        assertEquals(original.rating, restored.rating)
        assertEquals(original.purchasePrice!!, restored.purchasePrice!!, 0.0)
        assertEquals(original.lastPlayedAt, restored.lastPlayedAt)
        assertEquals(original.status, restored.status)
    }

    @Test
    fun `values fall back to the local snapshot when the server has none`() {
        // Item synchronisé avant A1bis : rien côté serveur, tout côté local.
        val original = vinylRecord()
        val localExtras = FacadeMapper.recordToRemote(original, "col-1", null).localExtras
        val bareRemote = RemoteItem(
            localId = "rec-1",
            collectionLocalId = "col-1",
            unifiedData = buildJsonObject { put("title", "Kind of Blue") },
            updatedAt = now
        )

        val restored = FacadeMapper.remoteToRecord(
            item = bareRemote,
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = localExtras,
            createdAt = now
        )

        assertEquals(original.recordingSpeed, restored.recordingSpeed)
        assertEquals(original.country, restored.country)
        assertEquals(original.rating, restored.rating)
        assertEquals(original.purchasePrice!!, restored.purchasePrice!!, 0.0)
        assertEquals(original.lastPlayedAt, restored.lastPlayedAt)
    }

    @Test
    fun `the server wins over the local snapshot`() {
        val localExtras = FacadeMapper.recordToRemote(vinylRecord(), "col-1", null).localExtras
        val remote = RemoteItem(
            localId = "rec-1",
            collectionLocalId = "col-1",
            unifiedData = buildJsonObject { put("country", "UK") },
            userData = buildJsonObject { put("rating", 2) },
            updatedAt = now
        )

        val restored = FacadeMapper.remoteToRecord(
            item = remote,
            type = CollectionType.VINYL,
            seriesId = null,
            localExtras = localExtras,
            createdAt = now
        )

        assertEquals("UK", restored.country)
        assertEquals(2, restored.rating)
    }

    @Test
    fun `manga unifiedData carries the extras that have a server home`() {
        val unified = FacadeMapper.recordToRemote(mangaRecord(), "col-1", "node-1").item.unifiedData
        assertEquals("Glénat", unified.string("publisherFr"))
        assertEquals(192, unified["pageCount"]!!.jsonPrimitive.int)
        assertEquals("1-3", unified.string("sourceVolumeRange"))
    }

    @Test
    fun `manga extras round-trip through the server, homeless ones through the snapshot`() {
        val original = mangaRecord()
        val facade = FacadeMapper.recordToRemote(original, "col-1", "node-1")
        val restored = FacadeMapper.remoteToRecord(
            item = facade.item,
            type = CollectionType.MANGA,
            seriesId = "series-1",
            localExtras = facade.localExtras,
            createdAt = now
        )

        // Modélisés côté serveur.
        assertEquals("Glénat", restored.extras[MangaExtras.KEY_PUBLISHER_FR])
        assertEquals("192", restored.extras[MangaExtras.KEY_PAGE_COUNT])
        assertEquals("1-3", restored.extras[MangaExtras.KEY_SOURCE_VOLUME_RANGE])
        // Sans home serveur : ne survivent que par le cliché local.
        assertEquals("seinen", restored.extras[MangaExtras.KEY_DEMOGRAPHIC])
        assertEquals("Éd. colossale", restored.extras[MangaExtras.KEY_EDITION])
    }

    private fun mangaRecord(): Record = Record(
        id = "rec-m1",
        collectionType = CollectionType.MANGA,
        seriesId = "series-1",
        volumeNumber = 1,
        artists = listOf(Artist("a1", "Naoki Urasawa")),
        title = "Monster T.1",
        isbn = "9782723456789",
        status = ItemStatus.OWNED,
        extras = mapOf(
            MangaExtras.KEY_PUBLISHER_FR to "Glénat",
            MangaExtras.KEY_PAGE_COUNT to "192",
            MangaExtras.KEY_SOURCE_VOLUME_RANGE to "1-3",
            MangaExtras.KEY_DEMOGRAPHIC to "seinen",
            MangaExtras.KEY_EDITION to "Éd. colossale"
        ),
        createdAt = now,
        updatedAt = now
    )

    private fun remoteItemWithUserData(status: String?): RemoteItem = RemoteItem(
        localId = "rec-1",
        collectionLocalId = "col-1",
        unifiedData = buildJsonObject { put("title", "Kind of Blue") },
        userData = buildJsonObject { status?.let { put("status", it) } },
        updatedAt = now
    )

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
