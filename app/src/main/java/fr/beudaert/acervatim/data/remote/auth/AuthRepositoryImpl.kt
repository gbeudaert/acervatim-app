package fr.beudaert.acervatim.data.remote.auth

import android.content.Context
import fr.beudaert.acervatim.data.remote.api.AcervatimApi
import fr.beudaert.acervatim.data.remote.api.AuthApi
import fr.beudaert.acervatim.data.remote.dto.GoogleLoginRequest
import fr.beudaert.acervatim.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val acervatimApi: AcervatimApi,
    private val googleSignInClient: GoogleSignInClient,
    private val tokenStore: TokenStore
) : AuthRepository {

    override val authState: StateFlow<AuthState> = tokenStore.authState

    override fun currentUserId(): String? = tokenStore.currentUserId()

    override suspend fun signInWithGoogle(activityContext: Context): Result<Unit> {
        val idToken = googleSignInClient.requestIdToken(activityContext)
            .getOrElse { return Result.failure(it) }
        return runCatching {
            val response = authApi.loginWithGoogle(GoogleLoginRequest(idToken = idToken))
            tokenStore.save(accessToken = response.accessToken, userId = response.userId)
        }
    }

    override fun signOut() = tokenStore.clear()

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        acervatimApi.deleteAccount()
        tokenStore.clear()
    }
}
