package app.acervatim.data.remote.oauth

import android.util.Log
import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.OAuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État des connexions de sources externes, médié par le backend. Cache **mémoire uniquement**
 * (comme le profil compte) : le backend reste la seule source de vérité, l'état repart vide après
 * un kill du process jusqu'au prochain [refresh].
 */
@Singleton
class OAuthRepositoryImpl @Inject constructor(
    private val api: AcervatimApi,
    private val authRepository: AuthRepository
) : OAuthRepository {

    private val cache = MutableStateFlow<Set<String>>(emptySet())

    override val connectedProviders: Flow<Set<String>> =
        combine(authRepository.authState, cache) { auth, connected ->
            // Hors session : aucune connexion visible, sans appel réseau.
            if (auth is AuthState.SignedIn) connected else emptySet()
        }.distinctUntilChanged()

    override suspend fun refresh() {
        if (authRepository.currentUserId() == null) {
            cache.value = emptySet()
            return
        }
        // Backend muet / hors-ligne ⇒ on garde l'état courant, jamais d'exception (offline-first).
        runCatching { api.connectedProviders().data.map { it.provider }.toSet() }
            .onSuccess { cache.value = it }
            .onFailure { Log.i(TAG, "providers connectés indisponibles: ${it.message}") }
    }

    override suspend fun startConnection(providerCode: String): Result<String> =
        runCatching { api.startOAuth(providerCode).authorizeUrl }

    override suspend fun disconnect(providerCode: String): Result<Unit> =
        runCatching {
            api.revokeOAuth(providerCode)
            cache.value = cache.value - providerCode
        }

    private companion object {
        const val TAG = "AcervatimOAuth"
    }
}
