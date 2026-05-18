package fr.beudaert.acervus.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RecordEntity::class,
        ArtistEntity::class,
        RecordArtistCrossRef::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AcervusDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun artistDao(): ArtistDao

    companion object {
        const val NAME: String = "acervus.db"
    }
}
