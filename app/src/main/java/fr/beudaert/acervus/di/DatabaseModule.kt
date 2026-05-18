package fr.beudaert.acervus.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.beudaert.acervus.data.local.db.AcervusDatabase
import fr.beudaert.acervus.data.local.db.ArtistDao
import fr.beudaert.acervus.data.local.db.MIGRATION_1_2
import fr.beudaert.acervus.data.local.db.RecordDao
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAcervusDatabase(@ApplicationContext context: Context): AcervusDatabase =
        Room.databaseBuilder(context, AcervusDatabase::class.java, AcervusDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecordDao(db: AcervusDatabase): RecordDao = db.recordDao()

    @Provides
    fun provideArtistDao(db: AcervusDatabase): ArtistDao = db.artistDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
