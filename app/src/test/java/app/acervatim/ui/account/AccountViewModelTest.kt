package app.acervatim.ui.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.model.account.InvitationRedeemResult
import app.acervatim.domain.model.account.PremiumSource
import app.acervatim.domain.model.account.PremiumStatus
import app.acervatim.domain.model.account.UserProfile
import app.acervatim.domain.model.sync.SyncStatus
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Robolectric : `signIn` exige un [Context] (impossible à instancier en JVM pure). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var auth: FakeAuthRepository
    private lateinit var sync: FakeSyncRepository
    private lateinit var account: FakeAccountRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        auth = FakeAuthRepository()
        sync = FakeSyncRepository()
        account = FakeAccountRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = AccountViewModel(auth, sync, account)

    @Test
    fun `init refreshes account info`() = runTest {
        vm()
        advanceUntilIdle()
        assertEquals(1, account.refreshCount)
    }

    @Test
    fun `uiState reflects account info from repository`() = runTest {
        auth.state.value = AuthState.SignedIn("u1")
        account.info.value = AccountInfo.Known(profile(premium = true), quota = null)

        vm().uiState.test {
            val state = expectMostRecentItem().let { first ->
                if (first.accountInfo is AccountInfo.Known) first else awaitItem()
            }
            assertTrue(state.accountInfo is AccountInfo.Known)
            assertTrue((state.accountInfo as AccountInfo.Known).profile.premium.isPremium)
        }
    }

    @Test
    fun `syncNow refreshes account info after the sync`() = runTest {
        val v = vm()
        advanceUntilIdle()
        val before = account.refreshCount

        v.syncNow()
        advanceUntilIdle()

        assertEquals(1, sync.syncCount)
        assertEquals(before + 1, account.refreshCount)
    }

    @Test
    fun `signIn success triggers sync then refresh`() = runTest {
        val v = vm()
        advanceUntilIdle()
        val before = account.refreshCount

        v.signIn(activityContext = ApplicationProvider.getApplicationContext())
        advanceUntilIdle()

        assertEquals(1, sync.syncCount)
        assertEquals(before + 1, account.refreshCount)
    }

    @Test
    fun `redeem maps repository result to redeem state`() = runTest {
        account.redeemResult = Result.success(InvitationRedeemResult(alreadyRedeemed = false, premiumGranted = true))
        val v = vm()

        v.redeemInvitation("CODE")
        advanceUntilIdle()
        assertEquals(RedeemUiState.Success, v.redeemState.value)

        account.redeemResult = Result.success(InvitationRedeemResult(alreadyRedeemed = true, premiumGranted = false))
        v.redeemInvitation("CODE")
        advanceUntilIdle()
        assertEquals(RedeemUiState.AlreadyRedeemed, v.redeemState.value)

        account.redeemResult = Result.failure(IllegalStateException("gone"))
        v.redeemInvitation("CODE")
        advanceUntilIdle()
        assertTrue(v.redeemState.value is RedeemUiState.Error)
    }

    @Test
    fun `blank redeem code is ignored`() = runTest {
        val v = vm()
        v.redeemInvitation("   ")
        advanceUntilIdle()
        assertEquals(RedeemUiState.Idle, v.redeemState.value)
        assertEquals(0, account.redeemCount)
    }

    private fun profile(premium: Boolean) = UserProfile(
        userId = "u1",
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        premium = PremiumStatus(
            isPremium = premium,
            source = if (premium) PremiumSource.GRANT else PremiumSource.NONE,
            expiresAt = null
        )
    )

    private class FakeAuthRepository : AuthRepository {
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
        override val authState: StateFlow<AuthState> = state
        override fun currentUserId(): String? = (state.value as? AuthState.SignedIn)?.userId
        override suspend fun signInWithGoogle(activityContext: Context): Result<Unit> {
            state.value = AuthState.SignedIn("u1")
            return Result.success(Unit)
        }
        override fun signOut() {
            state.value = AuthState.SignedOut
        }
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }

    private class FakeSyncRepository : SyncRepository {
        var syncCount = 0
        override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)
        override suspend fun syncNow(): Result<Unit> {
            syncCount++
            return Result.success(Unit)
        }
    }

    private class FakeAccountRepository : AccountRepository {
        val info = MutableStateFlow<AccountInfo>(AccountInfo.SignedOut)
        var refreshCount = 0
        var redeemCount = 0
        var redeemResult: Result<InvitationRedeemResult> =
            Result.success(InvitationRedeemResult(alreadyRedeemed = false, premiumGranted = false))

        override val accountInfo: Flow<AccountInfo> = info
        override suspend fun refresh() {
            refreshCount++
        }
        override suspend fun redeemInvitation(code: String): Result<InvitationRedeemResult> {
            redeemCount++
            return redeemResult
        }
    }
}
