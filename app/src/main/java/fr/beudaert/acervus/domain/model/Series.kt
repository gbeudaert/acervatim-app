package fr.beudaert.acervus.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class Series(
    val id: String,
    val collectionType: CollectionType,
    val title: String,
    val originalAuthor: String? = null,
    val publicationStatus: String? = null,
    val totalVolumes: Int? = null,
    val synopsis: String? = null,
    val coverPath: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
