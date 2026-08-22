package app.acervatim.ui.sharing

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.account.PremiumSource
import app.acervatim.domain.model.account.PremiumStatus
import app.acervatim.domain.model.account.UserProfile
import app.acervatim.domain.model.account.InvitationRedeemResult
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ShareScope
import app.acervatim.domain.model.sharing.ShareState
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.SharingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * JVM pur : le ViewModel ne touche ni au `Context` ni aux ressources. Les cas couverts sont ceux qui
 * font mal en production — la collection jamais synchronisée, le 402, et surtout le code de partage
 * qui ne doit **jamais** survivre à l'écran.
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

    private fun handle(type: CollectionType = CollectionType.VINYL) =
        SavedStateHandle(mapOf(SharingViewModel.COLLECTION_TYPE_ARG to type.name))

    private fun vm(type: CollectionType = CollectionType.VINYL) =
        SharingViewModel(sharing, account, handle(type))

    @Test
    fun `charge les partages de la collection du type demande`() = runTest {
        sharing.remoteIds[CollectionType.MANGA] = "col-manga"
        sharing.shares["col-manga"] = listOf(share(id = "s1", scope = ShareScope.WANTLIST))

        val v = vm(CollectionType.MANGA)
        advanceUntilIdle()

        val content = v.uiState.value.content
        assertTrue(content is SharingContent.Shares)
        assertEquals("s1", (content as SharingContent.Shares).shares.single().id)
        assertEquals(CollectionType.MANGA, v.uiState.value.collectionType)
    }

    @Test
    fun `collection jamais synchronisee - pas d'id serveur, pas d'appel reseau`() = runTest {
        val v = vm()
        advanceUntilIdle()

        assertEquals(SharingContent.NotSynced, v.uiState.value.content)
        assertEquals(0, sharing.listCount)
    }

    @Test
    fun `echec de chargement distingue de la liste vide`() = runTest {
        sharing.remoteIds[CollectionType.VINYL] = "col-1"
        sharing.listResult = Result.failure(SharingException(SharingFailure.NETWORK))

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharingContent.Failed, v.uiState.value.content)
        assertEquals(SharingFailure.NETWORK, v.uiState.value.error)
    }

    @Test
    fun `creation expose le code puis recharge la liste`() = runTest {
        sharing.remoteIds[CollectionType.VINYL] = "col-1"
        sharing.shares["col-1"] = emptyList()
        val v = vm()
        advanceUntilIdle()

        sharing.shares["col-1"] = listOf(share(id = "s1", scope = ShareScope.OWNED))
        v.createShare(ShareScope.OWNED, maxUses = 3, expiresAt = null)
        advanceUntilIdle()

        assertEquals("CODE-123", v.createdShare.value?.code)
        assertEquals(ShareScope.OWNED, sharing.lastCreate?.scope)
        assertEquals(3, sharing.lastCreate?.maxUses)
        val content = v.uiState.value.content
        assertEquals(1, (content as SharingContent.Shares).shares.size)
        assertFalse(v.uiState.value.busy)
    }

    @Test
    fun `le code est oublie des que l'ecran de resultat est quitte`() = runTest {
        sharing.remoteIds[CollectionType.VINYL] = "col-1"
        sharing.shares["col-1"] = emptyList()
        val savedState = handle()
        val v = SharingViewModel(sharing, account, savedState)
        advanceUntilIdle()

        v.createShare(ShareScope.ALL, maxUses = 1, expiresAt = null)
        advanceUntilIdle()
        assertEquals("CODE-123", v.createdShare.value?.code)

        // Rien du code ne doit avoir été confié à l'état sauvegardé (il survivrait au process death).
        assertTrue(savedState.keys().none { key -> savedState.get<Any?>(key).toString().contains("CODE-123") })

        v.consumeCreatedShare()
        assertNull(v.createdShare.value)
    }

    @Test
    fun `402 a la creation - erreur premium et perte du droit de creer`() = runTest {
        sharing.remoteIds[CollectionType.VINYL] = "col-1"
        sharing.shares["col-1"] = emptyList()
        val v = vm()
        advanceUntilIdle()
        assertTrue(v.uiState.value.canCreate)

        sharing.createResult = Result.failure(SharingException(SharingFailure.PREMIUM_REQUIRED))
        account.info.value = AccountInfo.Known(profile(premium = false))
        v.createShare(ShareScope.OWNED, maxUses = 1, expiresAt = null)
        advanceUntilIdle()

        assertEquals(SharingFailure.PREMIUM_REQUIRED, v.uiState.value.error)
        assertFalse(v.uiState.value.canCreate)
        assertNull(v.createdShare.value)
    }

    @Test
    fun `compte non premium - creation fermee, revocation ouverte`() = runTest {
        account.info.value = AccountInfo.Known(profile(premium = false))
        sharing.remoteIds[CollectionType.VINYL] = "col-1"
        sharing.shares["col-1"] = listOf(share(id = "s1", scope = ShareScope.ALL))

        val v = vm()
        advanceUntilIdle()
        assertFalse(v.uiState.value.canCreate)

        sharing.shares["col-1"] = emptyList()
        v.revokeShare("s1")
        advanceUntilIdle()

        assertEquals("s1", sharing.revokedShares.single())
        assertTrue((v.uiState.value.content as SharingContent.Shares).shares.isEmpty())
    }

    @Test
    fun `profil inconnu - on laisse tenter, le serveur tranchera`() = runTest {
        account.info.value = AccountInfo.Unknown
        sharing.remoteIds[CollectionType.VINYL] = "col-1"
        sharing.shares["col-1"] = emptyList()

        val v = vm()
        advanceUntilIdle()

        assertTrue(v.uiState.value.canCreate)
    }

    @Test
    fun `revocation d'un membre recharge la liste`() = runTest {
        sharing.remoteIds[CollectionType.VINYL] = "col-1"
        sharing.shares["col-1"] = listOf(share(id = "s1", scope = ShareScope.ALL))
        val v = vm()
        advanceUntilIdle()

        v.revokeMember("s1", "member-1")
        advanceUntilIdle()

        assertEquals("s1" to "member-1", sharing.revokedMembers.single())
    }

    @Test
    fun `l'etat d'un partage derive de l'expiration puis de l'epuisement`() {
        val now = Instant.parse("2026-08-21T12:00:00Z")
        val expired = share(id = "s", scope = ShareScope.ALL)
            .copy(expiresAt = now.minusSeconds(1), maxUses = 5, usedCount = 0)
        val exhausted = share(id = "s", scope = ShareScope.ALL)
            .copy(expiresAt = null, maxUses = 2, usedCount = 2)
        val active = share(id = "s", scope = ShareScope.ALL)
            .copy(expiresAt = now.plusSeconds(60), maxUses = 2, usedCount = 1)

        assertEquals(ShareState.EXPIRED, expired.stateAt(now))
        assertEquals(ShareState.EXHAUSTED, exhausted.stateAt(now))
        assertEquals(ShareState.ACTIVE, active.stateAt(now))
    }

    private fun share(id: String, scope: ShareScope) = CollectionShare(
        id = id,
        collectionId = "col-1",
        scope = scope,
        maxUses = 1,
        usedCount = 0,
        expiresAt = null,
        createdAt = Instant.parse("2026-08-21T10:00:00Z")
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

    private class FakeSharingRepository : SharingRepository {
        val remoteIds = mutableMapOf<CollectionType, String>()
        val shares = mutableMapOf<String, List<CollectionShare>>()
        val revokedShares = mutableListOf<String>()
        val revokedMembers = mutableListOf<Pair<String, String>>()
        var listCount = 0
        var lastCreate: CreateCall? = null
        var listResult: Result<List<CollectionShare>>? = null
        var createResult: Result<CreatedShare>? = null

        data class CreateCall(val scope: ShareScope, val maxUses: Int, val expiresAt: Instant?)

        override suspend fun remoteCollectionId(type: CollectionType): String? = remoteIds[type]

        override suspend fun shares(collectionId: String): Result<List<CollectionShare>> {
            listCount++
            return listResult ?: Result.success(shares[collectionId].orEmpty())
        }

        override suspend fun createShare(
            collectionId: String,
            scope: ShareScope,
            maxUses: Int,
            expiresAt: Instant?
        ): Result<CreatedShare> {
            lastCreate = CreateCall(scope, maxUses, expiresAt)
            return createResult ?: Result.success(
                CreatedShare(
                    id = "s1",
                    code = "CODE-123",
                    scope = scope,
                    maxUses = maxUses,
                    expiresAt = expiresAt
                )
            )
        }

        override suspend fun revokeShare(shareId: String): Result<Unit> {
            revokedShares += shareId
            return Result.success(Unit)
        }

        override suspend fun revokeMember(shareId: String, memberUserId: String): Result<Unit> {
            revokedMembers += shareId to memberUserId
            return Result.success(Unit)
        }
    }

    private class FakeAccountRepository : AccountRepository {
        val info = MutableStateFlow<AccountInfo>(AccountInfo.Unknown)
        var refreshCount = 0

        override val accountInfo: Flow<AccountInfo> = info
        override suspend fun refresh() {
            refreshCount++
        }

        override suspend fun redeemInvitation(code: String): Result<InvitationRedeemResult> =
            Result.success(InvitationRedeemResult(alreadyRedeemed = false, premiumGranted = false))
    }
}
