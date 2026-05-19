package fr.beudaert.acervus.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.RecordingSpeed
import java.time.Instant

@Entity(
    tableName = "records",
    indices = [
        Index(value = ["title"]),
        Index(value = ["status"]),
        Index(value = ["barcode"], unique = true),
        Index(value = ["collectionType"]),
        Index(value = ["seriesId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class RecordEntity(
    @PrimaryKey val id: String,
    val collectionType: CollectionType,
    val seriesId: String?,
    val volumeNumber: Int?,
    val title: String,
    val genre: String?,
    val year: Int?,
    val label: String?,
    val format: AcervusFormat?,
    val recordingSpeed: RecordingSpeed?,
    val country: String?,
    val purchasePrice: Double?,
    val rating: Int?,
    val coverPath: String?,
    val coverRemoteUrl: String?,
    val barcode: String?,
    val discogsId: Long?,
    val status: ItemStatus,
    val lastPlayedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)
