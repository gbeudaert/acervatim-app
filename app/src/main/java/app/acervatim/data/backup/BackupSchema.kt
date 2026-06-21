package app.acervatim.data.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * Schéma sérialisé du backup. Versionné — toute évolution incompatible doit incrémenter
 * [BackupSchemaV1.VERSION] et introduire un nouveau schéma + migration côté import.
 *
 * Ces DTOs sont volontairement découplés du modèle domain pour que le format
 * d'export reste stable même si les modèles évoluent.
 */
@Serializable
data class BackupSchemaV1(
    val version: Int = VERSION,
    @Serializable(with = InstantIsoSerializer::class)
    val exportedAt: Instant,
    val appVersionName: String? = null,
    val records: List<BackupRecordV1>,
    val series: List<BackupSeriesV1>
) {
    companion object {
        const val VERSION: Int = 1
    }
}

@Serializable
data class BackupRecordV1(
    val id: String,
    val collectionType: String,
    val seriesId: String? = null,
    val volumeNumber: Int? = null,
    val artists: List<String>,
    val title: String,
    val genre: String? = null,
    val year: Int? = null,
    val label: String? = null,
    val format: String? = null,
    val recordingSpeed: String? = null,
    val country: String? = null,
    val purchasePrice: Double? = null,
    val rating: Int? = null,
    /** Chemin relatif dans le zip (`covers/<id>.jpg`) si une cover locale est embarquée. */
    val coverInZip: String? = null,
    val coverRemoteUrl: String? = null,
    val barcode: String? = null,
    val isbn: String? = null,
    val discogsId: Long? = null,
    val status: String,
    @Serializable(with = InstantIsoSerializer::class)
    val lastPlayedAt: Instant? = null,
    val extras: Map<String, String> = emptyMap(),
    @Serializable(with = InstantIsoSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantIsoSerializer::class)
    val updatedAt: Instant
)

@Serializable
data class BackupSeriesV1(
    val id: String,
    val collectionType: String,
    val title: String,
    val originalAuthor: String? = null,
    val publicationStatus: String? = null,
    val totalVolumes: Int? = null,
    val synopsis: String? = null,
    val coverInZip: String? = null,
    @Serializable(with = InstantIsoSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantIsoSerializer::class)
    val updatedAt: Instant
)

internal object InstantIsoSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
