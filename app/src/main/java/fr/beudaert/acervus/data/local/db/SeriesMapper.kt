package fr.beudaert.acervus.data.local.db

import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.model.SeriesWithCounts

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

internal fun SeriesWithCountsRow.toDomain(): SeriesWithCounts = SeriesWithCounts(
    series = series.toDomain(),
    ownedVolumes = ownedVolumes,
    wishlistVolumes = wishlistVolumes,
    ignoredVolumes = ignoredVolumes,
    totalVolumesInLibrary = totalVolumesInLibrary
)
