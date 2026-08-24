package app.acervatim.ui.sharing.received

import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.ReceivedEntry
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.domain.model.sharing.RedeemedShare
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.ui.sharing.FakeSharingRepository
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
 * Côté membre : rejoindre par code, lister, renommer.
 *
 * Les cas d'erreur sont testés un par un parce que c'est précisément là que l'écran doit rester
 * honnête : le serveur confond volontairement les cinq refus de code, et l'app ne doit pas inventer
 * un diagnostic plus précis qu'elle n'en a.
 */
class ReceivedSharesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var sharing: FakeSharingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sharing = FakeSharingRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = ReceivedSharesViewModel(sharing)

    @Test
    fun `liste les partages rejoints avec mon libelle et les compteurs du serveur`() = runTest {
        sharing.received = listOf(
            received("s1", "Wantlist manga de Bob", entry("col-1", "Mangas", 12))
        )

        val v = vm()
        advanceUntilIdle()

        val shares = (v.uiState.value.content as ReceivedContent.Shares).shares
        assertEquals("Wantlist manga de Bob", shares.single().label)
        assertEquals(12, shares.single().collections.single().itemCount)
    }

    @Test
    fun `rejoindre transmet le code et mon libelle, puis recharge`() = runTest {
        val v = vm()
        advanceUntilIdle()
        val before = sharing.receivedCount

        v.join("  CODE-123  ", "Chez Bob")
        advanceUntilIdle()

        assertEquals("  CODE-123  " to "Chez Bob", sharing.lastRedeem)
        assertTrue(sharing.receivedCount > before)
    }

    @Test
    fun `code vide - aucun appel reseau`() = runTest {
        val v = vm()
        advanceUntilIdle()

        v.join("   ", null)
        advanceUntilIdle()

        assertNull(sharing.lastRedeem)
    }

    @Test
    fun `deja membre - ce n'est pas une erreur, mais ca se dit`() = runTest {
        sharing.redeemResult = Result.success(
            RedeemedShare(
                share = received("s1", null),
                alreadyMember = true
            )
        )
        val v = vm()
        advanceUntilIdle()

        v.join("CODE-123", null)
        advanceUntilIdle()

        assertTrue(v.uiState.value.alreadyMember)
        assertNull(v.uiState.value.error)

        v.consumeAlreadyMember()
        assertFalse(v.uiState.value.alreadyMember)
    }

    @Test
    fun `code refuse - un seul diagnostic, celui que le serveur donne`() = runTest {
        sharing.redeemResult = Result.failure(SharingException(SharingFailure.CODE_INVALID))
        val v = vm()
        advanceUntilIdle()

        v.join("CODE-123", null)
        advanceUntilIdle()

        assertEquals(SharingFailure.CODE_INVALID, v.uiState.value.error)
    }

    @Test
    fun `son propre partage - refus nomme, distinct d'un code invalide`() = runTest {
        sharing.redeemResult = Result.failure(SharingException(SharingFailure.OWN_SHARE))
        val v = vm()
        advanceUntilIdle()

        v.join("CODE-123", null)
        advanceUntilIdle()

        assertEquals(SharingFailure.OWN_SHARE, v.uiState.value.error)
    }

    @Test
    fun `trop de tentatives - la defense anti-balayage a son propre message`() = runTest {
        sharing.redeemResult = Result.failure(SharingException(SharingFailure.TOO_MANY_ATTEMPTS))
        val v = vm()
        advanceUntilIdle()

        v.join("CODE-123", null)
        advanceUntilIdle()

        assertEquals(SharingFailure.TOO_MANY_ATTEMPTS, v.uiState.value.error)
    }

    @Test
    fun `renommer un partage recu ne touche que mon cote`() = runTest {
        sharing.received = listOf(received("s1", null, entry("col-1", "Mangas", 3)))
        val v = vm()
        advanceUntilIdle()

        v.rename("s1", "Chez Bob")
        advanceUntilIdle()

        assertEquals("s1" to "Chez Bob", sharing.renamedReceived.single())
    }

    @Test
    fun `partage revoque pendant l'action - la liste est resynchronisee`() = runTest {
        sharing.received = listOf(received("s1", null))
        val v = vm()
        advanceUntilIdle()
        val before = sharing.receivedCount

        sharing.renameReceivedResult = Result.failure(SharingException(SharingFailure.GONE))
        v.rename("s1", "Disparu")
        advanceUntilIdle()

        assertEquals(SharingFailure.GONE, v.uiState.value.error)
        assertTrue(sharing.receivedCount > before)
    }

    @Test
    fun `echec de chargement distingue de la liste vide`() = runTest {
        sharing.receivedResult = Result.failure(SharingException(SharingFailure.NETWORK))

        val v = vm()
        advanceUntilIdle()

        assertEquals(ReceivedContent.Failed, v.uiState.value.content)
        assertEquals(SharingFailure.NETWORK, v.uiState.value.error)
    }

    private fun entry(id: String, name: String, count: Int) = ReceivedEntry(
        collectionId = id,
        name = name,
        typeCode = "manga",
        statuses = setOf(ItemStatus.WISHLIST),
        itemCount = count
    )

    private fun received(id: String, label: String?, vararg entries: ReceivedEntry) = ReceivedShare(
        shareId = id,
        label = label,
        redeemedAt = Instant.parse("2026-08-22T10:00:00Z"),
        collections = entries.toList()
    )
}
