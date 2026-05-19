package fr.beudaert.acervus.data.local.db

import fr.beudaert.acervus.domain.model.Series

internal fun SeriesEntity.toDomain(): Series = Series(
    id = id,
    collectionType = collectionType,
    title = title,
    originalAuthor = originalAuthor,
    publicationStatus = publicationStatus,
    totalVolumes = totalVolumes,
    synopsis = synopsis,
    coverPath = coverPath,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun Series.toEntity(): SeriesEntity = SeriesEntity(
    id = id,
    collectionType = collectionType,
    title = title,
    originalAuthor = originalAuthor,
    publicationStatus = publicationStatus,
    totalVolumes = totalVolumes,
    synopsis = synopsis,
    coverPath = coverPath,
    createdAt = createdAt,
    updatedAt = updatedAt
)
