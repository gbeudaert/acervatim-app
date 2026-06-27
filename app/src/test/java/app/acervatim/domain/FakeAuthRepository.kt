package app.acervatim.domain

import android.content.Context
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake minimal d'[AuthRepository] pour les tests : pilote l'état de session par `userId`. */
class FakeAuthRepository(initialUserId: String? = null) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(
        initialUserId?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var userId: String? = initialUserId

    fun setSignedIn(id: String) {
        userId = id
        _authState.value = AuthState.SignedIn(id)
    }

    override fun currentUserId(): String? = userId

    override suspend fun signInWithGoogle(activityContext: Context): Result<Unit> =
        Result.success(Unit)

    override fun signOut() {
        userId = null
        _authState.value = AuthState.SignedOut
    }

    override suspend fun deleteAccount(): Result<Unit> {
        signOut()
        return Result.success(Unit)
    }
}
