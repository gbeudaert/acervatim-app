package fr.beudaert.acervatim.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervatim.data.remote.auth.AuthState
import fr.beudaert.acervatim.data.remote.auth.SignInCancelledException
import fr.beudaert.acervatim.domain.model.sync.SyncStatus
import fr.beudaert.acervatim.domain.repository.AuthRepository
import fr.beudaert.acervatim.domain.repository.SyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val authState: AuthState = AuthState.SignedOut,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val signInError: SignInError? = null
)

enum class SignInError { CANCELLED, FAILED }

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val signInErrorFlow = kotlinx.coroutines.flow.MutableStateFlow<SignInError?>(null)

    val uiState: StateFlow<AccountUiState> =
        combine(authRepository.authState, syncRepository.status, signInErrorFlow) { auth, sync, error ->
            AccountUiState(authState = auth, syncStatus = sync, signInError = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun signIn(activityContext: Context) {
        signInErrorFlow.value = null
        viewModelScope.launch {
            authRepository.signInWithGoogle(activityContext)
                .onSuccess { syncRepository.syncNow() }
                .onFailure { error ->
                    signInErrorFlow.value =
                        if (error is SignInCancelledException) SignInError.CANCELLED else SignInError.FAILED
                }
        }
    }

    fun syncNow() {
        viewModelScope.launch { syncRepository.syncNow() }
    }

    fun signOut() = authRepository.signOut()

    fun deleteAccount() {
        viewModelScope.launch { authRepository.deleteAccount() }
    }

    fun consumeSignInError() {
        signInErrorFlow.value = null
    }
}
