package fr.beudaert.acervatim.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.beudaert.acervatim.data.backup.BackupCoverIo
import fr.beudaert.acervatim.data.backup.BackupCoverIoImpl
import fr.beudaert.acervatim.data.backup.BackupGatewayImpl
import fr.beudaert.acervatim.data.local.storage.CoverStorageImpl
import fr.beudaert.acervatim.domain.repository.BackupGateway
import fr.beudaert.acervatim.domain.repository.CoverStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindCoverStorage(impl: CoverStorageImpl): CoverStorage

    @Binds
    @Singleton
    abstract fun bindBackupCoverIo(impl: BackupCoverIoImpl): BackupCoverIo

    @Binds
    @Singleton
    abstract fun bindBackupGateway(impl: BackupGatewayImpl): BackupGateway
}
