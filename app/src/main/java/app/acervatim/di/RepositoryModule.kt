package app.acervatim.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.acervatim.data.remote.account.AccountRepositoryImpl
import app.acervatim.data.remote.auth.AuthRepositoryImpl
import app.acervatim.data.remote.oauth.OAuthRepositoryImpl
import app.acervatim.data.remote.search.ExternalSearchRepositoryImpl
import app.acervatim.data.remote.sync.SyncManagerImpl
import app.acervatim.data.repository.RecordRepositoryImpl
import app.acervatim.data.repository.SeriesRepositoryImpl
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.ExternalSearchRepository
import app.acervatim.domain.repository.OAuthRepository
import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.repository.SeriesRepository
import app.acervatim.domain.repository.SyncRepository
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

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindExternalSearchRepository(
        impl: ExternalSearchRepositoryImpl
    ): ExternalSearchRepository

    @Binds
    @Singleton
    abstract fun bindOAuthRepository(impl: OAuthRepositoryImpl): OAuthRepository
}
