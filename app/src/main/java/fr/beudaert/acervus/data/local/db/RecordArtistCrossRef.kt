package fr.beudaert.acervus.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "record_artists",
    primaryKeys = ["recordId", "artistId"],
    indices = [
        Index(value = ["recordId"]),
        Index(value = ["artistId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecordArtistCrossRef(
    val recordId: String,
    val artistId: String,
    val position: Int
)
