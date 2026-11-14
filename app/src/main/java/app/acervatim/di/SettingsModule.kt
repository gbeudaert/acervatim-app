package app.acervatim.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import app.acervatim.data.local.settings.DataStoreSeriesCatchUpStore
import app.acervatim.data.local.settings.DataStoreSettingsRepository
import app.acervatim.data.local.settings.SeriesCatchUpDataStore
import app.acervatim.domain.repository.SeriesCatchUpStore
import app.acervatim.domain.repository.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("acervatim_settings") }
        )

    /** Fichier à part : l'état de rattrapage n'est pas une préférence (cf. [DataStoreSeriesCatchUpStore]). */
    @Provides
    @Singleton
    @SeriesCatchUpDataStore
    fun provideSeriesCatchUpDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("acervatim_series_catch_up") }
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSeriesCatchUpStore(impl: DataStoreSeriesCatchUpStore): SeriesCatchUpStore
}
