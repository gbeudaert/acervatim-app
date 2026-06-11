package fr.beudaert.acervatim.data.remote.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage chiffré du JWT applicatif + `userId`, et source de vérité de l'[AuthState].
 *
 * - Implémente [SessionTokenProvider] (lecture synchrone pour l'intercepteur réseau).
 * - Implémente [UnauthorizedHandler] : un `401` efface le token et bascule en [AuthState.SessionExpired].
 *
 * Le token est conservé en `EncryptedSharedPreferences` (AES-256). Aucune donnée locale (collections,
 * items) n'est touchée à la déconnexion : l'app reste utilisable hors-ligne.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context
) : SessionTokenProvider, UnauthorizedHandler {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _authState = MutableStateFlow<AuthState>(readInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun readInitialState(): AuthState {
        val token = prefs.getString(KEY_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        return if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
            AuthState.SignedIn(userId)
        } else {
            AuthState.SignedOut
        }
    }

    override fun currentAccessToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Identifiant utilisateur courant (UUID back), `null` si déconnecté. */
    fun currentUserId(): String? = prefs.getString(KEY_USER_ID, null)

    /** Enregistre une nouvelle session après un login réussi. */
    fun save(accessToken: String, userId: String) {
        prefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_USER_ID, userId)
            .apply()
        _authState.value = AuthState.SignedIn(userId)
    }

    /** Déconnexion volontaire : efface le token et repasse en [AuthState.SignedOut]. */
    fun clear() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.SignedOut
    }

    override fun onUnauthorized() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.SessionExpired
    }

    private companion object {
        const val PREFS_NAME = "acervatim_session"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
    }
}
