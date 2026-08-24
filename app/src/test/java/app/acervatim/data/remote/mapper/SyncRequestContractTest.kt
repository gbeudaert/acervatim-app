package app.acervatim.data.remote.mapper

import app.acervatim.data.remote.dto.SourceRefDto
import app.acervatim.domain.model.sync.RemoteItem
import app.acervatim.domain.model.sync.RemoteNode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Contrat des corps de requête de synchronisation, backend **0.8.1** (synchro des mangas).
 *
 * Ces cas portent sur du JSON réellement sérialisé, avec la **configuration du client** — le
 * `explicitNulls = false` de `NetworkModule` est précisément ce qui rend « effacer » et « ne pas
 * toucher » indistinguables si on n'y prend pas garde. Un test sur les objets Kotlin seuls
 * passerait sans rien prouver.
 *
 * Le `SyncManager` lui-même est testé côté Robolectric (base Room) ; ici on verrouille ce qui part
 * sur le réseau.
 */
class SyncRequestContractTest {

    /** Copie conforme de `NetworkModule.provideJson()`. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
        isLenient = true
    }

    private fun item(
        nodeLocalId: String? = null,
        nodeRemoteId: String? = null,
        volume: Int? = null
    ) = RemoteItem(
        localId = "l1",
        collectionLocalId = "c1",
        nodeLocalId = nodeLocalId,
        nodeRemoteId = nodeRemoteId,
        volume = volume,
        unifiedData = buildJsonObject { put("title", "Berserk T.1") },
        updatedAt = Instant.EPOCH
    )

    private fun node(unified: Boolean = true) = RemoteNode(
        localId = "s1",
        collectionLocalId = "c1",
        level = "serie",
        unifiedData = if (unified) buildJsonObject { put("title", "Berserk") } else buildJsonObject { },
        updatedAt = Instant.EPOCH
    )

    // ---- POST /collections/:id/items -------------------------------------------------------------

    @Test
    fun `creation d'un tome - la synchro rattache par id serveur`() {
        val body = RemoteDtoMapper.run { item(nodeRemoteId = "srv-node").toCreateRequest(nodeRemoteId = "srv-node") }
        val encoded = json.encodeToString(body)

        assertTrue(encoded.contains("\"nodeId\":\"srv-node\""))
        assertFalse("`node` et `nodeId` sont exclusifs", encoded.contains("\"node\""))
    }

    @Test
    fun `creation d'un tome - l'import enrichi rattache par reference provider`() {
        val body = RemoteDtoMapper.run {
            item().toCreateRequest(nodeRef = SourceRefDto("mal", "42"))
        }
        val encoded = json.encodeToString(body)

        assertTrue(encoded.contains("\"node\""))
        assertFalse(encoded.contains("\"nodeId\""))
    }

    @Test
    fun `id serveur et reference provider ensemble - l'id serveur gagne, jamais les deux`() {
        val body = RemoteDtoMapper.run {
            item().toCreateRequest(nodeRef = SourceRefDto("mal", "42"), nodeRemoteId = "srv-node")
        }
        assertNull(body.node)
        assertEquals("srv-node", body.nodeId)
    }

    @Test
    fun `creation d'un vinyle - aucun rattachement de serie`() {
        val body = RemoteDtoMapper.run { item().toCreateRequest() }
        val encoded = json.encodeToString(body)

        assertFalse(encoded.contains("\"node\""))
        assertFalse(encoded.contains("\"nodeId\""))
    }

    // ---- PATCH /items/:id --------------------------------------------------------------------------

    @Test
    fun `patch d'un tome - la serie et le numero sont transmis`() {
        val body = RemoteDtoMapper.run {
            item(nodeRemoteId = "srv-node", volume = 3).toPatchRequest(serial = true)
        }
        val encoded = json.encodeToString(body)

        assertTrue(encoded.contains("\"nodeId\":\"srv-node\""))
        assertTrue(encoded.contains("\"volume\":3"))
    }

    /**
     * Le cœur du sujet : un tome détaché de sa série doit envoyer `null` **explicitement**. Omettre
     * la clé laisserait le serveur sur l'ancienne valeur, et le détachement resterait local.
     */
    @Test
    fun `patch d'un tome detache - null explicite, pas une cle absente`() {
        val body = RemoteDtoMapper.run {
            item(nodeRemoteId = null, volume = null).toPatchRequest(serial = true)
        }
        val encoded = json.encodeToString(body)

        assertTrue("nodeId doit valoir null, pas être absent", encoded.contains("\"nodeId\":null"))
        assertTrue("volume doit valoir null, pas être absent", encoded.contains("\"volume\":null"))
    }

    @Test
    fun `patch d'un vinyle - ni serie ni numero, que le serveur refuserait`() {
        val body = RemoteDtoMapper.run { item().toPatchRequest(serial = false) }
        val encoded = json.encodeToString(body)

        assertFalse(encoded.contains("\"nodeId\""))
        assertFalse(encoded.contains("\"volume\""))
    }

    // ---- POST/PATCH nodes --------------------------------------------------------------------------

    @Test
    fun `creation d'une serie saisie a la main - unifiedData seul, sans source`() {
        val body = RemoteDtoMapper.run { node().toCreateRequest() }
        val encoded = json.encodeToString(body)

        assertTrue(encoded.contains("\"unifiedData\""))
        assertFalse(encoded.contains("\"source\""))
    }

    /**
     * `isWishlist: false` sur un nœud sans tome **supprime le nœud** côté serveur. L'app ne modélise
     * pas ce drapeau : le taire est la seule façon de ne pas purger des séries dont les tomes ne
     * sont pas encore poussés.
     */
    @Test
    fun `les requetes de serie ne portent jamais isWishlist`() {
        val created = json.encodeToString(RemoteDtoMapper.run { node().toCreateRequest() })
        val patched = json.encodeToString(RemoteDtoMapper.run { node().toPatchRequest() })

        assertFalse("création", created.contains("isWishlist"))
        assertFalse("patch", patched.contains("isWishlist"))
    }

    @Test
    fun `une serie sans verite curee n'envoie pas un unifiedData vide`() {
        val body = RemoteDtoMapper.run { node(unified = false).toCreateRequest() }
        assertNull(body.unifiedData)
    }
}
