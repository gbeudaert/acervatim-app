package fr.beudaert.acervatim.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RecordEntity::class,
        ArtistEntity::class,
        RecordArtistCrossRef::class,
        SeriesEntity::class,
        CollectionEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AcervatimDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun artistDao(): ArtistDao
    abstract fun seriesDao(): SeriesDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME: String = "acervatim.db"
    }
}
