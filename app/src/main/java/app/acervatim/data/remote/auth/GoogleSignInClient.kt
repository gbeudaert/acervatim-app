package app.acervatim.data.remote.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import app.acervatim.R
import app.acervatim.domain.model.auth.SignInCancelledException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Récupère un `id_token` Google via le **Credential Manager** (androidx.credentials + googleid).
 *
 * Le `serverClientId` est l'ID client **OAuth de type Web** (non secret) configuré dans la console
 * Google Cloud, fourni en ressource `R.string.google_server_client_id`. Le `id_token` obtenu est
 * ensuite échangé contre un JWT applicatif par [AuthRepository] (`POST /v1/auth/google`).
 *
 * `getCredential` doit s'exécuter avec un **contexte d'Activity** (la feuille système s'y rattache) :
 * il est donc passé au moment de l'appel depuis l'écran Compose (`LocalContext.current`).
 */
@Singleton
class GoogleSignInClient @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val serverClientId: String get() = appContext.getString(R.string.google_server_client_id)

    suspend fun requestIdToken(activityContext: Context): Result<String> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            // false : autorise un premier sign-up même sans compte déjà lié à l'app.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = response.credential
        val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
        googleIdCredential.idToken
    }.recoverCatching { error ->
        when (error) {
            is GetCredentialCancellationException -> throw SignInCancelledException()
            is GetCredentialException -> throw error
            else -> throw error
        }
    }
}
