package app.acervatim.domain.repository

import android.content.Context
import app.acervatim.domain.model.auth.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * Session applicative : connexion Google → JWT, déconnexion, suppression de compte. L'app reste
 * 100 % utilisable sans session (offline-first) ; se connecter active la synchronisation.
 */
interface AuthRepository {

    val authState: StateFlow<AuthState>

    /** UUID utilisateur back de la session courante, `null` si déconnecté. */
    fun currentUserId(): String?

    /**
     * Flow complet : ouvre la feuille Google (besoin d'un contexte d'Activity), échange l'`id_token`
     * contre un JWT et l'enregistre. `Result.failure(SignInCancelledException)` si l'utilisateur annule.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<Unit>

    /** Déconnexion volontaire : efface le token (les données locales sont conservées). */
    fun signOut()

    /** `DELETE /v1/me` (cascade côté back) puis efface la session locale. */
    suspend fun deleteAccount(): Result<Unit>
}
