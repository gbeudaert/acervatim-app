package fr.beudaert.acervus.data.local.db

import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.model.SeriesWithCounts
import fr.beudaert.acervus.domain.model.normalizeArtistName

/**
 * Normalisation utilisée pour la clé naturelle d'une série
 * (`collectionType, normalizedTitle, normalizedAuthor`).
 * Réutilise [normalizeArtistName] (NFD + lowercase + suppression des diacritiques).
 * Pour un auteur null/vide, renvoie `""` afin que l'index unique fonctionne (SQLite
 * traite NULL != NULL dans les contraintes uniques).
 */
internal fun normalizeSeriesKey(value: String?): String =
    normalizeArtistName(value.orEmpty())

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
    normalizedTitle = normalizeSeriesKey(title),
    normalizedAuthor = normalizeSeriesKey(originalAuthor),
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
