package fr.beudaert.acervus.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.beudaert.acervus.domain.model.CollectionType
import java.time.Instant

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["collectionType"]),
        Index(value = ["title"])
    ]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val collectionType: CollectionType,
    val title: String,
    val originalAuthor: String?,
    val publicationStatus: String?,
    val totalVolumes: Int?,
    val synopsis: String?,
    val coverPath: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
