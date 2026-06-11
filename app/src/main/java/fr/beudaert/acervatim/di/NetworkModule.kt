package fr.beudaert.acervatim.di

import fr.beudaert.acervatim.BuildConfig
import fr.beudaert.acervatim.data.remote.api.AcervatimApi
import fr.beudaert.acervatim.data.remote.api.AuthApi
import fr.beudaert.acervatim.data.remote.auth.SessionTokenProvider
import fr.beudaert.acervatim.data.remote.auth.TokenStore
import fr.beudaert.acervatim.data.remote.auth.UnauthorizedHandler
import fr.beudaert.acervatim.data.remote.interceptor.AuthInterceptor
import fr.beudaert.acervatim.data.remote.interceptor.ErrorInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * `Json` partagé réseau : tolérant en lecture (`ignoreUnknownKeys`, le contrat peut s'enrichir)
     * et minimal en écriture (`encodeDefaults = false`, `explicitNulls = false`) pour respecter la
     * validation `.strict()` du back (n'émettre que les champs renseignés).
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideSessionTokenProvider(store: TokenStore): SessionTokenProvider = store

    @Provides
    @Singleton
    fun provideUnauthorizedHandler(store: TokenStore): UnauthorizedHandler = store

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        errorInterceptor: ErrorInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(errorInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAcervatimApi(retrofit: Retrofit): AcervatimApi = retrofit.create(AcervatimApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}
