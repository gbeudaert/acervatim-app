package app.acervatim.data.remote.interceptor

import app.acervatim.data.remote.auth.SessionTokenProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ajoute `Authorization: Bearer <accessToken>` aux requêtes qui le requièrent (§1).
 *
 * Une requête peut **opter out** via le header marqueur [NO_AUTH_HEADER] (login Google, `/health`) ;
 * le marqueur est retiré avant l'envoi.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenProvider: SessionTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        if (original.header(NO_AUTH_HEADER) != null) {
            val stripped = original.newBuilder().removeHeader(NO_AUTH_HEADER).build()
            return chain.proceed(stripped)
        }

        val token = tokenProvider.currentAccessToken()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }

    companion object {
        /** Header marqueur (jamais envoyé) signalant qu'une route est publique. */
        const val NO_AUTH_HEADER = "X-Acervatim-No-Auth"
    }
}
