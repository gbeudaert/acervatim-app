package fr.beudaert.acervus.data.local.db

import androidx.room.TypeConverter
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.RecordingSpeed
import java.time.Instant

class Converters {

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun vinylFormatToString(value: AcervusFormat?): String? = value?.name

    @TypeConverter
    fun stringToAcervusFormat(value: String?): AcervusFormat? = value?.let { AcervusFormat.valueOf(it) }

    @TypeConverter
    fun recordingSpeedToString(value: RecordingSpeed?): String? = value?.name

    @TypeConverter
    fun stringToRecordingSpeed(value: String?): RecordingSpeed? =
        value?.let { RecordingSpeed.valueOf(it) }

    @TypeConverter
    fun itemStatusToString(value: ItemStatus): String = value.name

    @TypeConverter
    fun stringToItemStatus(value: String): ItemStatus = ItemStatus.valueOf(value)

    @TypeConverter
    fun collectionTypeToString(value: CollectionType): String = value.name

    @TypeConverter
    fun stringToCollectionType(value: String): CollectionType = CollectionType.valueOf(value)
}
