package fr.beudaert.acervus.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RecordEntity::class,
        ArtistEntity::class,
        RecordArtistCrossRef::class,
        SeriesEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AcervusDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun artistDao(): ArtistDao
    abstract fun seriesDao(): SeriesDao

    companion object {
        const val NAME: String = "acervus.db"
    }
}
