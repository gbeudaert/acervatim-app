package app.acervatim.data.backup

import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.model.Series
import java.util.UUID

internal fun Record.toBackupDto(coverInZip: String?): BackupRecordV1 = BackupRecordV1(
    id = id,
    collectionType = collectionType.name,
    seriesId = seriesId,
    volumeNumber = volumeNumber,
    artists = artists.map { it.name },
    title = title,
    genre = genre,
    year = year,
    label = label,
    format = format?.name,
    recordingSpeed = recordingSpeed?.name,
    country = country,
    purchasePrice = purchasePrice,
    rating = rating,
    coverInZip = coverInZip,
    coverRemoteUrl = coverRemoteUrl,
    barcode = barcode,
    isbn = isbn,
    discogsId = discogsId,
    status = status.name,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    extras = extras,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun Series.toBackupDto(coverInZip: String?): BackupSeriesV1 = BackupSeriesV1(
    id = id,
    collectionType = collectionType.name,
    title = title,
    originalAuthor = originalAuthor,
    publicationStatus = publicationStatus,
    totalVolumes = totalVolumes,
    synopsis = synopsis,
    coverInZip = coverInZip,
    createdAt = createdAt,
    updatedAt = updatedAt,
    edition = edition,
    publisherFr = publisherFr,
    mangaId = mangaId
)

/**
 * Reconstruit un [Record] à partir du DTO. Les valeurs d'enum inconnues (schéma plus
 * récent que l'app) tombent sur `null` / `OTHER` plutôt que faire échouer l'import.
 *
 * [resolvedCoverPath] = chemin absolu du fichier cover restauré, ou null si pas de cover.
 */
internal fun BackupRecordV1.toRecord(resolvedCoverPath: String?): Record = Record(
    id = id,
    collectionType = parseCollectionType(collectionType),
    seriesId = seriesId,
    volumeNumber = volumeNumber,
    artists = artists
        .filter { it.isNotBlank() }
        .map { Artist(id = UUID.randomUUID().toString(), name = it) },
    title = title,
    genre = genre,
    year = year,
    label = label,
    format = format?.let { parseEnumOrNull<AcervatimFormat>(it) },
    recordingSpeed = recordingSpeed?.let { parseEnumOrNull<RecordingSpeed>(it) },
    country = country,
    purchasePrice = purchasePrice,
    rating = rating,
    coverPath = resolvedCoverPath,
    coverRemoteUrl = coverRemoteUrl,
    barcode = barcode,
    isbn = isbn,
    discogsId = discogsId,
    status = parseEnumOrNull<ItemStatus>(status) ?: ItemStatus.OWNED,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    extras = extras,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun BackupSeriesV1.toSeries(resolvedCoverPath: String?): Series = Series(
    id = id,
    collectionType = parseCollectionType(collectionType),
    title = title,
    originalAuthor = originalAuthor,
    publicationStatus = publicationStatus,
    totalVolumes = totalVolumes,
    synopsis = synopsis,
    coverPath = resolvedCoverPath,
    edition = edition,
    publisherFr = publisherFr,
    mangaId = mangaId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun parseCollectionType(raw: String): CollectionType =
    parseEnumOrNull<CollectionType>(raw) ?: CollectionType.VINYL

private inline fun <reified E : Enum<E>> parseEnumOrNull(raw: String): E? =
    runCatching { enumValueOf<E>(raw) }.getOrNull()
