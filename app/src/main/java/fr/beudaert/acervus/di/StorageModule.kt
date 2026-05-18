package fr.beudaert.acervus.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.beudaert.acervus.data.local.storage.CoverStorage
import fr.beudaert.acervus.data.local.storage.CoverStorageImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindCoverStorage(impl: CoverStorageImpl): CoverStorage
}
