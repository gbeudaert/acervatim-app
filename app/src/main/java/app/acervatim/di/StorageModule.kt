package app.acervatim.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.acervatim.data.backup.BackupCoverIo
import app.acervatim.data.backup.BackupCoverIoImpl
import app.acervatim.data.backup.BackupGatewayImpl
import app.acervatim.data.local.storage.CoverStorageImpl
import app.acervatim.domain.repository.BackupGateway
import app.acervatim.domain.repository.CoverStorage
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
