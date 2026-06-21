package app.acervatim.data.remote.account

import android.content.Context
import app.cash.turbine.test
import app.acervatim.data.remote.dto.MeResponse
import app.acervatim.data.remote.dto.PremiumDto
import app.acervatim.data.remote.dto.QuotaBucketDto
import app.acervatim.data.remote.dto.QuotaResponse
import app.acervatim.data.remote.dto.RedeemInvitationResponse
import app.acervatim.data.remote.error.ApiException
import app.acervatim.data.remote.sync.FakeAcervatimApi
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric requis : `refresh()` loge via `android.util.Log` sur les chemins de dégradation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountRepositoryImplTest {

    private val api = FakeAcervatimApi()

    private fun me(premium: Boolean = false) = MeResponse(
        userId = "u1",
        createdAt = "2026-01-15T10:00:00Z",
        premium = PremiumDto(isPremium = premium, source = if (premium) "grant" else "none")
    )

    private fun quota() = QuotaResponse(
        collections = QuotaBucketDto(used = 1, max = 3),
        items = QuotaBucketDto(used = 5, max = 50)
    )

    @Test
    fun `signed out emits SignedOut and refresh is a no-op`() = runTest {
        val auth = FakeAuthRepository(userId = null)
        val repo = AccountRepositoryImpl(api, auth)

        repo.refresh() // ne doit appeler aucune route (meHandler lèverait NotImplementedError)

        repo.accountInfo.test {
            assertEquals(AccountInfo.SignedOut, awaitItem())
        }
    }

    @Test
    fun `signed in without refresh emits Unknown`() = runTest {
        val repo = AccountRepositoryImpl(api, FakeAuthRepository(userId = "u1"))

        repo.accountInfo.test {
            assertEquals(AccountInfo.Unknown, awaitItem())
        }
    }

    @Test
    fun `refresh success emits Known with profile and quota`() = runTest {
        api.meHandler = { me(premium = true) }
        api.quotaHandler = { quota() }
        val repo = AccountRepositoryImpl(api, FakeAuthRepository(userId = "u1"))

        repo.refresh()

        repo.accountInfo.test {
            val info = awaitItem()
            assertTrue(info is AccountInfo.Known)
            info as AccountInfo.Known
            assertEquals("u1", info.profile.userId)
            assertTrue(info.profile.premium.isPremium)
            assertEquals(3, info.quota?.collections?.max)
        }
    }

    @Test
    fun `me failure degrades to Unknown without throwing`() = runTest {
        api.meHandler = { throw ApiException.NotFound(null) }
        val repo = AccountRepositoryImpl(api, FakeAuthRepository(userId = "u1"))

        repo.refresh() // ne doit pas lever

        repo.accountInfo.test {
            assertEquals(AccountInfo.Unknown, awaitItem())
        }
    }

    @Test
    fun `quota failure keeps Known profile with null quota`() = runTest {
        api.meHandler = { me() }
        api.quotaHandler = { throw ApiException.Generic(500, null, "boom") }
        val repo = AccountRepositoryImpl(api, FakeAuthRepository(userId = "u1"))

        repo.refresh()

        repo.accountInfo.test {
            val info = awaitItem()
            assertTrue(info is AccountInfo.Known)
            assertNull((info as AccountInfo.Known).quota)
        }
    }

    @Test
    fun `sign out after refresh flips back to SignedOut`() = runTest {
        api.meHandler = { me() }
        api.quotaHandler = { quota() }
        val auth = FakeAuthRepository(userId = "u1")
        val repo = AccountRepositoryImpl(api, auth)
        repo.refresh()

        repo.accountInfo.test {
            assertTrue(awaitItem() is AccountInfo.Known)
            auth.state.value = AuthState.SignedOut
            assertEquals(AccountInfo.SignedOut, awaitItem())
        }
    }

    @Test
    fun `redeem granting premium triggers a refresh`() = runTest {
        api.meHandler = { me(premium = true) }
        api.quotaHandler = { quota() }
        api.redeemHandler = { RedeemInvitationResponse(alreadyRedeemed = false, premiumGranted = true) }
        val repo = AccountRepositoryImpl(api, FakeAuthRepository(userId = "u1"))

        val result = repo.redeemInvitation("CODE")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().premiumGranted)
        repo.accountInfo.test {
            val info = awaitItem()
            assertTrue(info is AccountInfo.Known && info.profile.premium.isPremium)
        }
    }

    @Test
    fun `redeem failure surfaces the ApiException in the Result`() = runTest {
        api.redeemHandler = { throw ApiException.Gone(null) }
        val repo = AccountRepositoryImpl(api, FakeAuthRepository(userId = "u1"))

        val result = repo.redeemInvitation("OLD")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException.Gone)
    }

    private class FakeAuthRepository(userId: String?) : AuthRepository {
        val state = MutableStateFlow<AuthState>(
            if (userId != null) AuthState.SignedIn(userId) else AuthState.SignedOut
        )
        override val authState: StateFlow<AuthState> = state
        override fun currentUserId(): String? = (state.value as? AuthState.SignedIn)?.userId
        override suspend fun signInWithGoogle(activityContext: Context): Result<Unit> = Result.success(Unit)
        override fun signOut() {
            state.value = AuthState.SignedOut
        }
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }
}
