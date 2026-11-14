package app.acervatim.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.model.auth.SignInCancelledException
import app.acervatim.domain.model.sync.SyncStatus
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.SyncRepository
import app.acervatim.domain.usecase.ClearSeriesCatchUpUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val authState: AuthState = AuthState.SignedOut,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val accountInfo: AccountInfo = AccountInfo.SignedOut,
    val signInError: SignInError? = null
)

enum class SignInError { CANCELLED, FAILED }

/** Cycle de vie du formulaire « code d'invitation ». */
sealed interface RedeemUiState {
    data object Idle : RedeemUiState
    data object Loading : RedeemUiState
    data object Success : RedeemUiState
    data object AlreadyRedeemed : RedeemUiState
    data class Error(val message: String?) : RedeemUiState
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val accountRepository: AccountRepository,
    private val clearSeriesCatchUp: ClearSeriesCatchUpUseCase
) : ViewModel() {

    private val signInErrorFlow = MutableStateFlow<SignInError?>(null)

    private val _redeemState = MutableStateFlow<RedeemUiState>(RedeemUiState.Idle)
    val redeemState: StateFlow<RedeemUiState> = _redeemState.asStateFlow()

    val uiState: StateFlow<AccountUiState> =
        combine(
            authRepository.authState,
            syncRepository.status,
            accountRepository.accountInfo,
            signInErrorFlow
        ) { auth, sync, account, error ->
            AccountUiState(authState = auth, syncStatus = sync, accountInfo = account, signInError = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    init {
        // No-op si déconnecté ; sinon recharge profil + quota à l'ouverture de l'écran.
        viewModelScope.launch { accountRepository.refresh() }
    }

    fun signIn(activityContext: Context) {
        signInErrorFlow.value = null
        viewModelScope.launch {
            authRepository.signInWithGoogle(activityContext)
                .onSuccess {
                    syncRepository.syncNow()
                    accountRepository.refresh()
                }
                .onFailure { error ->
                    signInErrorFlow.value =
                        if (error is SignInCancelledException) SignInError.CANCELLED else SignInError.FAILED
                }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncRepository.syncNow()
            // Le push fait bouger le `used` des quotas côté serveur.
            accountRepository.refresh()
        }
    }

    fun signOut() {
        authRepository.signOut()
        // L'état de rattrapage des séries a été calculé pour ce compte : il part avec la session.
        viewModelScope.launch { clearSeriesCatchUp() }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            authRepository.deleteAccount().onSuccess { clearSeriesCatchUp() }
        }
    }

    fun redeemInvitation(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        _redeemState.value = RedeemUiState.Loading
        viewModelScope.launch {
            accountRepository.redeemInvitation(trimmed)
                .onSuccess { result ->
                    _redeemState.value = when {
                        result.alreadyRedeemed -> RedeemUiState.AlreadyRedeemed
                        result.premiumGranted -> RedeemUiState.Success
                        else -> RedeemUiState.Error(null)
                    }
                }
                .onFailure { error -> _redeemState.value = RedeemUiState.Error(error.message) }
        }
    }

    fun consumeSignInError() {
        signInErrorFlow.value = null
    }
}
