package app.acervatim.data.local.db

import androidx.room.TypeConverter
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.model.sync.SyncState
import org.json.JSONObject
import java.time.Instant

class Converters {

    @TypeConverter
    fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun vinylFormatToString(value: AcervatimFormat?): String? = value?.name

    @TypeConverter
    fun stringToAcervatimFormat(value: String?): AcervatimFormat? = value?.let { AcervatimFormat.valueOf(it) }

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

    /**
     * `Map<String, String>` ⇄ JSON. On utilise `org.json` (présent dans Android SDK) plutôt qu'une
     * dépendance externe : aucune ambiguïté sur les types, et la perte de valeurs non-String est
     * volontaire (les extras restent textuels par contrat).
     */
    @TypeConverter
    fun extrasToString(value: Map<String, String>): String =
        if (value.isEmpty()) "{}" else JSONObject(value.toMap()).toString()

    @TypeConverter
    fun stringToExtras(value: String): Map<String, String> {
        if (value.isBlank() || value == "{}") return emptyMap()
        val obj = JSONObject(value)
        val keys = obj.keys()
        val result = LinkedHashMap<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = obj.optString(key, "")
        }
        return result
    }
}
