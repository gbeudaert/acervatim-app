package fr.beudaert.acervus.data.local.db

import androidx.room.TypeConverter
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.RecordStatus
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
    fun recordStatusToString(value: RecordStatus): String = value.name

    @TypeConverter
    fun stringToRecordStatus(value: String): RecordStatus = RecordStatus.valueOf(value)
}
