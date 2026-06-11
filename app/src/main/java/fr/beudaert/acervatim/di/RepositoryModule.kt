package fr.beudaert.acervatim.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.beudaert.acervatim.data.remote.auth.AuthRepositoryImpl
import fr.beudaert.acervatim.data.remote.sync.SyncManagerImpl
import fr.beudaert.acervatim.data.repository.RecordRepositoryImpl
import fr.beudaert.acervatim.data.repository.SeriesRepositoryImpl
import fr.beudaert.acervatim.domain.repository.AuthRepository
import fr.beudaert.acervatim.domain.repository.RecordRepository
import fr.beudaert.acervatim.domain.repository.SeriesRepository
import fr.beudaert.acervatim.domain.repository.SyncRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRecordRepository(impl: RecordRepositoryImpl): RecordRepository

    @Binds
    @Singleton
    abstract fun bindSeriesRepository(impl: SeriesRepositoryImpl): SeriesRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncManagerImpl): SyncRepository
}
