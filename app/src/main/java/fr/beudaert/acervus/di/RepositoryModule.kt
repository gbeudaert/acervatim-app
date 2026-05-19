package fr.beudaert.acervus.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.beudaert.acervus.data.repository.RecordRepositoryImpl
import fr.beudaert.acervus.data.repository.SeriesRepositoryImpl
import fr.beudaert.acervus.domain.repository.RecordRepository
import fr.beudaert.acervus.domain.repository.SeriesRepository
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
}
