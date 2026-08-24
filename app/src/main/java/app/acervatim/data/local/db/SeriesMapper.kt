package app.acervatim.data.local.db

import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.model.normalizeArtistName
import app.acervatim.domain.model.sync.SyncState
import java.time.Instant

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

internal fun Series.toEntity(
    collectionId: String,
    remoteId: String? = null,
    syncState: SyncState = SyncState.LOCAL_ONLY,
    serverUpdatedAt: Instant? = null
): SeriesEntity = SeriesEntity(
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
    updatedAt = updatedAt,
    collectionId = collectionId,
    remoteId = remoteId,
    syncState = syncState,
    serverUpdatedAt = serverUpdatedAt
)

internal fun SeriesWithCountsRow.toDomain(): SeriesWithCounts = SeriesWithCounts(
    series = series.toDomain(),
    ownedVolumes = ownedVolumes,
    wishlistVolumes = wishlistVolumes,
    ignoredVolumes = ignoredVolumes,
    totalVolumesInLibrary = totalVolumesInLibrary,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount
)
