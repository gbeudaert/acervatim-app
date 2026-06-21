package app.acervatim.data.remote.account

import android.util.Log
import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.RedeemInvitationRequest
import app.acervatim.data.remote.mapper.AccountMapper.toDomain
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.account.InvitationRedeemResult
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profil/quota backend, cache **mémoire uniquement** : pas de Room ni DataStore — après un kill du
 * process, le profil repart en [AccountInfo.Unknown] jusqu'au prochain [refresh], ce qui est
 * exactement la dégradation voulue (le backend reste la seule source de vérité premium/quota).
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val api: AcervatimApi,
    private val authRepository: AuthRepository
) : AccountRepository {

    /** Snapshot `(userId → info)` : invalidé implicitement quand la session change d'utilisateur. */
    private val cache = MutableStateFlow<Pair<String, AccountInfo>?>(null)

    override val accountInfo: Flow<AccountInfo> =
        combine(authRepository.authState, cache) { auth, cached ->
            when (auth) {
                is AuthState.SignedIn ->
                    cached?.takeIf { it.first == auth.userId }?.second ?: AccountInfo.Unknown
                AuthState.SignedOut, AuthState.SessionExpired -> AccountInfo.SignedOut
            }
        }.distinctUntilChanged()

    override suspend fun refresh() {
        val userId = authRepository.currentUserId() ?: return
        // Backend muet (404 si endpoint absent, 5xx, réseau…) ⇒ Unknown, jamais d'exception : aucune
        // fonctionnalité ne doit dépendre de cette réponse. Le 401 est déjà traité par ErrorInterceptor.
        val profile = runCatching { api.me().toDomain() }.getOrElse { error ->
            Log.i(TAG, "profil indisponible: ${error.message}")
            cache.value = userId to AccountInfo.Unknown
            return
        }
        val quota = runCatching { api.quota().toDomain() }.getOrElse { error ->
            Log.i(TAG, "quota indisponible: ${error.message}")
            null
        }
        cache.value = userId to AccountInfo.Known(profile, quota)
    }

    override suspend fun redeemInvitation(code: String): Result<InvitationRedeemResult> =
        runCatching { api.redeemInvitation(RedeemInvitationRequest(code)).toDomain() }
            .onSuccess { result ->
                if (result.premiumGranted) refresh()
            }

    private companion object {
        const val TAG = "AcervatimAccount"
    }
}
