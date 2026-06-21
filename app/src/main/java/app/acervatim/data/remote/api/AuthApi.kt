package app.acervatim.data.remote.api

import app.acervatim.data.remote.dto.AuthTokenResponse
import app.acervatim.data.remote.dto.GoogleLoginRequest
import app.acervatim.data.remote.dto.HealthResponse
import app.acervatim.data.remote.interceptor.AuthInterceptor
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

/** Routes publiques (sans JWT) du contrat (§3, §12). */
interface AuthApi {

    /** Échange un `id_token` Google contre un JWT applicatif. Crée le compte au premier login. */
    @Headers("${AuthInterceptor.NO_AUTH_HEADER}: 1")
    @POST("v1/auth/google")
    suspend fun loginWithGoogle(@Body body: GoogleLoginRequest): AuthTokenResponse

    /** Liveness (hors `/v1`). */
    @Headers("${AuthInterceptor.NO_AUTH_HEADER}: 1")
    @GET("health")
    suspend fun health(): HealthResponse
}
