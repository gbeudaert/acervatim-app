package app.acervatim.ui.sharing

import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.account.PremiumSource
import app.acervatim.domain.model.account.PremiumStatus
import app.acervatim.domain.model.account.UserProfile
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.ShareEntry
import app.acervatim.domain.model.sharing.ShareScopePreset
import app.acervatim.domain.model.sharing.ShareState
import app.acervatim.domain.model.sharing.SharableCollection
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.model.sharing.canonical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * JVM pur : le ViewModel ne touche ni au `Context` ni aux ressources.
 *
 * Les cas couverts sont ceux qui font mal en production — rien de synchronisé, le 402, le partage
 * disparu sous les pieds, et surtout le code de partage qui ne doit **jamais** survivre à l'écran.
 */
class SharingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sharing: FakeSharingRepository
    private lateinit var account: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sharing = FakeSharingRepository()
        account = FakeAccountRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = SharingViewModel(sharing, account)

    @Test
    fun `charge les partages du compte, toutes collections confondues`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        sharing.shares = listOf(
            share(
                id = "s1",
                entries = listOf(
                    entry("col-1", "Vinyles", setOf(ItemStatus.OWNED)),
                    entry("col-2", "Mangas", setOf(ItemStatus.WISHLIST))
                )
            )
        )

        val v = vm()
        advanceUntilIdle()

        val content = v.uiState.value.content
        assertTrue(content is SharingContent.Shares)
        val loaded = (content as SharingContent.Shares).shares.single()
        assertEquals("s1", loaded.id)
        assertEquals(listOf("col-1", "col-2"), loaded.collections.map { it.collectionId })
    }

    @Test
    fun `rien de synchronise - pas d'id serveur, pas d'appel reseau`() = runTest {
        val v = vm()
        advanceUntilIdle()

        assertEquals(SharingContent.NotSynced, v.uiState.value.content)
        assertEquals(0, sharing.listCount)
    }

    @Test
    fun `echec de chargement distingue de la liste vide`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        sharing.listResult = Result.failure(SharingException(SharingFailure.NETWORK))

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharingContent.Failed, v.uiState.value.content)
        assertEquals(SharingFailure.NETWORK, v.uiState.value.error)
    }

    @Test
    fun `creation - un seul code pour plusieurs collections, statuts au choix`() = runTest {
        sharing.sharable = listOf(
            collection("col-1", "Vinyles", "vinyl"),
            collection("col-2", "Mangas", "manga")
        )
        val v = vm()
        advanceUntilIdle()

        v.createShare(
            entries = mapOf(
                "col-1" to setOf(ItemStatus.OWNED, ItemStatus.WISHLIST),
                "col-2" to setOf(ItemStatus.WISHLIST)
            ),
            label = "Foyer",
            maxUses = 3,
            expiresAt = null
        )
        advanceUntilIdle()

        val call = sharing.lastCreate!!
        assertEquals(setOf("col-1", "col-2"), call.entries.keys)
        assertEquals(setOf(ItemStatus.OWNED, ItemStatus.WISHLIST), call.entries.getValue("col-1"))
        assertEquals("Foyer", call.label)
        assertEquals(3, call.maxUses)
        assertEquals("CODE-123", v.createdShare.value?.code)
        assertFalse(v.uiState.value.busy)
    }

    @Test
    fun `une collection sans statut coche n'est pas partagee`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        val v = vm()
        advanceUntilIdle()

        v.createShare(
            entries = mapOf("col-1" to setOf(ItemStatus.OWNED), "col-2" to emptySet()),
            label = null,
            maxUses = 1,
            expiresAt = null
        )
        advanceUntilIdle()

        assertEquals(setOf("col-1"), sharing.lastCreate!!.entries.keys)
    }

    @Test
    fun `tout decoche - aucune creation tentee`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        val v = vm()
        advanceUntilIdle()

        v.createShare(mapOf("col-1" to emptySet()), label = null, maxUses = 1, expiresAt = null)
        advanceUntilIdle()

        assertNull(sharing.lastCreate)
        assertNull(v.createdShare.value)
    }

    @Test
    fun `le code ne vit qu'en memoire et s'oublie a la fermeture`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        val v = vm()
        advanceUntilIdle()

        v.createShare(mapOf("col-1" to setOf(ItemStatus.OWNED)), null, 1, null)
        advanceUntilIdle()
        assertEquals("CODE-123", v.createdShare.value?.code)

        // La liste rechargée depuis le serveur ne porte jamais le code : il n'existe qu'ici.
        val listed = (v.uiState.value.content as? SharingContent.Shares)?.shares.orEmpty()
        assertTrue(listed.none { it.toString().contains("CODE-123") })

        v.consumeCreatedShare()
        assertNull(v.createdShare.value)
    }

    @Test
    fun `402 a la creation - erreur premium et perte du droit de creer`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        val v = vm()
        advanceUntilIdle()
        assertTrue(v.uiState.value.canCreate)

        sharing.createResult = Result.failure(SharingException(SharingFailure.PREMIUM_REQUIRED))
        account.info.value = AccountInfo.Known(profile(premium = false))
        v.createShare(mapOf("col-1" to setOf(ItemStatus.OWNED)), null, 1, null)
        advanceUntilIdle()

        assertEquals(SharingFailure.PREMIUM_REQUIRED, v.uiState.value.error)
        assertFalse(v.uiState.value.canCreate)
        assertNull(v.createdShare.value)
    }

    @Test
    fun `compte non premium - creation fermee, renommage et revocation ouverts`() = runTest {
        account.info.value = AccountInfo.Known(profile(premium = false))
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        sharing.shares = listOf(share("s1", listOf(entry("col-1", "Vinyles", setOf(ItemStatus.OWNED)))))

        val v = vm()
        advanceUntilIdle()
        assertFalse(v.uiState.value.canCreate)

        v.renameShare("s1", "Chez Alice")
        advanceUntilIdle()
        assertEquals("s1" to "Chez Alice", sharing.renamed.single())

        sharing.shares = emptyList()
        v.revokeShare("s1")
        advanceUntilIdle()

        assertEquals("s1", sharing.revokedShares.single())
        assertTrue((v.uiState.value.content as SharingContent.Shares).shares.isEmpty())
    }

    @Test
    fun `profil inconnu - on laisse tenter, le serveur tranchera`() = runTest {
        account.info.value = AccountInfo.Unknown
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))

        val v = vm()
        advanceUntilIdle()

        assertTrue(v.uiState.value.canCreate)
    }

    @Test
    fun `renommer avec une chaine vide efface le libelle`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        sharing.shares = listOf(share("s1", listOf(entry("col-1", "Vinyles", setOf(ItemStatus.OWNED)))))
        val v = vm()
        advanceUntilIdle()

        v.renameShare("s1", "")
        advanceUntilIdle()

        assertEquals("s1" to "", sharing.renamed.single())
    }

    @Test
    fun `404 sur une action - la liste est resynchronisee au lieu d'etre crue`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        sharing.shares = listOf(share("s1", listOf(entry("col-1", "Vinyles", setOf(ItemStatus.OWNED)))))
        val v = vm()
        advanceUntilIdle()
        val before = sharing.listCount

        sharing.renameResult = Result.failure(SharingException(SharingFailure.GONE))
        v.renameShare("s1", "Disparu")
        advanceUntilIdle()

        assertEquals(SharingFailure.GONE, v.uiState.value.error)
        assertTrue("la liste doit avoir été rechargée", sharing.listCount > before)
    }

    @Test
    fun `404 a la creation - collection inconnue du serveur, pas un partage disparu`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        val v = vm()
        advanceUntilIdle()

        // Le serveur ne reconnaît pas la collection envoyée : l'app tient un `remoteId` périmé.
        // Le dire « ce partage n'existe plus » serait faux — il n'y en a pas encore.
        sharing.createResult = Result.failure(SharingException(SharingFailure.COLLECTION_GONE))
        sharing.sharable = emptyList()
        v.createShare(mapOf("col-1" to setOf(ItemStatus.OWNED)), null, 1, null)
        advanceUntilIdle()

        assertEquals(SharingFailure.COLLECTION_GONE, v.uiState.value.error)
        // C'est le sélecteur de collections qu'il faut refaire, pas seulement la liste des partages.
        assertTrue(v.uiState.value.sharable.isEmpty())
        assertEquals(SharingContent.NotSynced, v.uiState.value.content)
    }

    @Test
    fun `revocation d'un membre recharge la liste`() = runTest {
        sharing.sharable = listOf(collection("col-1", "Vinyles", "vinyl"))
        sharing.shares = listOf(share("s1", listOf(entry("col-1", "Vinyles", setOf(ItemStatus.OWNED)))))
        val v = vm()
        advanceUntilIdle()

        v.revokeMember("s1", "member-1")
        advanceUntilIdle()

        assertEquals("s1" to "member-1", sharing.revokedMembers.single())
    }

    @Test
    fun `l'etat d'un partage derive de l'expiration puis de l'epuisement`() {
        val now = Instant.parse("2026-08-22T12:00:00Z")
        val base = share("s", listOf(entry("col-1", "Vinyles", setOf(ItemStatus.OWNED))))
        val expired = base.copy(expiresAt = now.minusSeconds(1), maxUses = 5, usedCount = 0)
        val exhausted = base.copy(expiresAt = null, maxUses = 2, usedCount = 2)
        val active = base.copy(expiresAt = now.plusSeconds(60), maxUses = 2, usedCount = 1)

        assertEquals(ShareState.EXPIRED, expired.stateAt(now))
        assertEquals(ShareState.EXHAUSTED, exhausted.stateAt(now))
        assertEquals(ShareState.ACTIVE, active.stateAt(now))
    }

    @Test
    fun `les raccourcis d'UI ne sont que des ensembles de statuts`() {
        assertEquals(ShareScopePreset.ALL, ShareScopePreset.matching(ItemStatus.entries.toSet()))
        assertEquals(ShareScopePreset.OWNED, ShareScopePreset.matching(setOf(ItemStatus.OWNED)))
        assertEquals(ShareScopePreset.WANTLIST, ShareScopePreset.matching(setOf(ItemStatus.WISHLIST)))
        // « Possédés et désirés, sans les ignorés » : exactement ce que les portées interdisaient.
        assertNull(ShareScopePreset.matching(setOf(ItemStatus.OWNED, ItemStatus.WISHLIST)))
    }

    @Test
    fun `l'ordre canonique ne depend pas de l'ordre de cochage`() {
        val a = setOf(ItemStatus.WISHLIST, ItemStatus.OWNED).canonical()
        val b = setOf(ItemStatus.OWNED, ItemStatus.WISHLIST).canonical()
        assertEquals(a, b)
        assertEquals(listOf(ItemStatus.OWNED, ItemStatus.WISHLIST), a)
    }

    private fun collection(id: String, name: String, type: String) =
        SharableCollection(collectionId = id, name = name, typeCode = type)

    private fun entry(id: String, name: String, statuses: Set<ItemStatus>) =
        ShareEntry(collectionId = id, name = name, typeCode = "vinyl", statuses = statuses)

    private fun share(id: String, entries: List<ShareEntry>) = CollectionShare(
        id = id,
        label = null,
        maxUses = 1,
        usedCount = 0,
        expiresAt = null,
        createdAt = Instant.parse("2026-08-22T10:00:00Z"),
        collections = entries
    )

    private fun profile(premium: Boolean) = UserProfile(
        userId = "u1",
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        premium = PremiumStatus(
            isPremium = premium,
            source = if (premium) PremiumSource.GRANT else PremiumSource.NONE,
            expiresAt = null
        )
    )
}
