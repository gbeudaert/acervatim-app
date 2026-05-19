package fr.beudaert.acervus.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class Record(
    val id: String,
    val collectionType: CollectionType = CollectionType.VINYL,
    val seriesId: String? = null,
    val volumeNumber: Int? = null,
    val artists: List<Artist>,
    val title: String,
    val genre: String? = null,
    val year: Int? = null,
    val label: String? = null,
    val format: AcervusFormat? = null,
    val recordingSpeed: RecordingSpeed? = null,
    val country: String? = null,
    val purchasePrice: Double? = null,
    val rating: Int? = null,
    val coverPath: String? = null,
    val coverRemoteUrl: String? = null,
    val barcode: String? = null,
    val discogsId: Long? = null,
    val status: ItemStatus = ItemStatus.OWNED,
    val lastPlayedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /** Artiste principal (position 0). Vide uniquement pour une [Record] mal construite. */
    val primaryArtist: Artist? get() = artists.firstOrNull()

    /** Artistes additionnels au-delà du principal — affichés en "feat. …". */
    val featuringArtists: List<Artist> get() = if (artists.size <= 1) emptyList() else artists.drop(1)
}
