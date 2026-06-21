package app.acervatim.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import app.acervatim.data.local.db.AcervatimDatabase
import app.acervatim.data.local.db.ArtistDao
import app.acervatim.data.local.db.CollectionDao
import app.acervatim.data.local.db.MIGRATION_1_2
import app.acervatim.data.local.db.RecordDao
import app.acervatim.data.local.db.SeriesDao
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAcervatimDatabase(@ApplicationContext context: Context): AcervatimDatabase =
        Room.databaseBuilder(context, AcervatimDatabase::class.java, AcervatimDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            // Pré-v1 : pas de migration historique (cf. politique migrations). La montée du
            // schéma miroir (v6) repart d'une base propre en dev ; à retirer avant la v1 publiée.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecordDao(db: AcervatimDatabase): RecordDao = db.recordDao()

    @Provides
    fun provideArtistDao(db: AcervatimDatabase): ArtistDao = db.artistDao()

    @Provides
    fun provideSeriesDao(db: AcervatimDatabase): SeriesDao = db.seriesDao()

    @Provides
    fun provideCollectionDao(db: AcervatimDatabase): CollectionDao = db.collectionDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
