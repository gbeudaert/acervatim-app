package app.acervatim.data.remote.interceptor

import android.util.Log
import app.acervatim.data.remote.auth.UnauthorizedHandler
import app.acervatim.data.remote.dto.ProblemDetailsDto
import app.acervatim.data.remote.error.ApiException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Convertit toute réponse non-2xx en [ApiException] typée à partir du corps `application/problem+json`
 * (§2), et notifie le [UnauthorizedHandler] sur `401`. Logge le `X-Request-Id` de chaque réponse
 * (succès comme erreur) pour le support (§15.8).
 *
 * Placé en intercepteur d'application : il voit la réponse finale (après auth) et lève l'exception
 * avant que Retrofit ne produise une `HttpException` générique.
 */
@Singleton
class ErrorInterceptor @Inject constructor(
    private val json: Json,
    private val unauthorizedHandler: UnauthorizedHandler
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        response.header(REQUEST_ID_HEADER)?.let { requestId ->
            Log.d(TAG, "${response.code} ${chain.request().url} — X-Request-Id=$requestId")
        }

        if (response.isSuccessful) return response

        val problem = response.peekProblem()
        if (response.code == 401) unauthorizedHandler.onUnauthorized()

        // On consomme le corps pour lever l'exception ; la réponse n'est plus transmise à Retrofit.
        response.close()
        throw ApiException.from(response.code, problem, fallbackMessage = response.message)
    }

    private fun Response.peekProblem(): ProblemDetailsDto? = runCatching {
        // peekBody : lit le corps sans le consommer pour la suite (borné, l'erreur tient < 256 kB).
        val raw = peekBody(MAX_ERROR_BODY_BYTES).string()
        if (raw.isBlank()) null else json.decodeFromString(ProblemDetailsDto.serializer(), raw)
    }.getOrNull()

    companion object {
        private const val TAG = "AcervatimApi"
        private const val REQUEST_ID_HEADER = "X-Request-Id"
        private const val MAX_ERROR_BODY_BYTES = 256L * 1024
    }
}
