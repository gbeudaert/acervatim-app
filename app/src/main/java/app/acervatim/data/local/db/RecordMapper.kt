package app.acervatim.data.local.db

import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.sync.SyncState
import java.time.Instant

internal fun RecordEntity.toDomain(artists: List<Artist>): Record = Record(
    id = id,
    collectionType = collectionType,
    seriesId = seriesId,
    volumeNumber = volumeNumber,
    artists = artists,
    title = title,
    genre = genre,
    year = year,
    label = label,
    format = format,
    recordingSpeed = recordingSpeed,
    country = country,
    purchasePrice = purchasePrice,
    rating = rating,
    coverPath = coverPath,
    coverRemoteUrl = coverRemoteUrl,
    barcode = barcode,
    isbn = isbn,
    discogsId = discogsId,
    status = status,
    lastPlayedAt = lastPlayedAt,
    extras = extras,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Le domaine [Record] ne porte pas les métadonnées de sync (elles vivent dans la couche data).
 * Le repository fournit donc [collectionId] et l'état de sync à préserver / mettre à jour.
 */
internal fun Record.toEntity(
    collectionId: String,
    remoteId: String? = null,
    syncState: SyncState = SyncState.LOCAL_ONLY,
    serverUpdatedAt: Instant? = null
): RecordEntity = RecordEntity(
    id = id,
    collectionType = collectionType,
    seriesId = seriesId,
    volumeNumber = volumeNumber,
    title = title,
    genre = genre,
    year = year,
    label = label,
    format = format,
    recordingSpeed = recordingSpeed,
    country = country,
    purchasePrice = purchasePrice,
    rating = rating,
    coverPath = coverPath,
    coverRemoteUrl = coverRemoteUrl,
    barcode = barcode,
    isbn = isbn,
    discogsId = discogsId,
    status = status,
    lastPlayedAt = lastPlayedAt,
    extras = extras,
    createdAt = createdAt,
    updatedAt = updatedAt,
    collectionId = collectionId,
    remoteId = remoteId,
    syncState = syncState,
    serverUpdatedAt = serverUpdatedAt
)

/**
 * Regroupe une liste plate de rows JOIN en `List<Record>`, un Record par identifiant,
 * artistes triés par `position`. L'ordre des records dans la liste suit l'ordre d'apparition
 * dans `rows` (la première ligne d'un record détermine sa place).
 */
internal fun List<RecordWithArtistsRow>.groupToRecords(): List<Record> {
    if (isEmpty()) return emptyList()
    val orderedIds = LinkedHashSet<String>()
    val rowsByRecord = LinkedHashMap<String, MutableList<RecordWithArtistsRow>>()
    for (row in this) {
        orderedIds += row.record.id
        rowsByRecord.getOrPut(row.record.id) { mutableListOf() } += row
    }
    return orderedIds.map { id ->
        val recordRows = rowsByRecord.getValue(id).sortedBy { it.position }
        val artists = recordRows.map { Artist(id = it.artistId, name = it.artistName) }
        recordRows.first().record.toDomain(artists)
    }
}
