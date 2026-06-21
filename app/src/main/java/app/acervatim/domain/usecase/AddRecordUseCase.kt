package app.acervatim.domain.usecase

import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.normalizeIsbn
import app.acervatim.domain.repository.RecordRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Signale qu'un autre [Record] possède déjà cette valeur unique.
 * Le caller (ViewModel) est responsable de surfacer un message + offrir « ouvrir l'existant ».
 */
class DuplicateRecordException(
    val existingRecordId: String,
    val field: Field,
    message: String
) : Exception(message) {
    enum class Field { BARCODE, ISBN }
}

class AddRecordUseCase @Inject constructor(
    private val repository: RecordRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(input: RecordInput, id: String = UUID.randomUUID().toString()): Record {
        val cleanedArtists = input.artists.map(String::trim).filter { it.isNotEmpty() }.distinct()
        require(cleanedArtists.isNotEmpty()) { "at least one artist is required" }
        require(input.title.isNotBlank()) { "title must not be blank" }
        input.rating?.let { require(it in RATING_RANGE) { "rating must be in $RATING_RANGE" } }

        val barcode = input.barcode?.trim()?.takeIf { it.isNotEmpty() }
        val isbn = normalizeIsbn(input.isbn)

        // Pre-check unicité côté domaine pour fournir une erreur typée au lieu d'une SQLite
        // ConstraintException brute.
        if (barcode != null) {
            repository.findByBarcode(barcode)?.let { existing ->
                throw DuplicateRecordException(
                    existingRecordId = existing.id,
                    field = DuplicateRecordException.Field.BARCODE,
                    message = "Barcode $barcode already exists (id=${existing.id})"
                )
            }
        }
        if (isbn != null) {
            repository.findByIsbn(isbn)?.let { existing ->
                throw DuplicateRecordException(
                    existingRecordId = existing.id,
                    field = DuplicateRecordException.Field.ISBN,
                    message = "ISBN $isbn already exists (id=${existing.id})"
                )
            }
        }

        val now = clock.instant()
        val record = Record(
            id = id,
            collectionType = input.collectionType,
            seriesId = input.seriesId,
            volumeNumber = input.volumeNumber,
            artists = cleanedArtists.map { Artist(id = "", name = it) },
            title = input.title.trim(),
            genre = input.genre?.trim()?.takeIf { it.isNotEmpty() },
            year = input.year,
            label = input.label?.trim()?.takeIf { it.isNotEmpty() },
            format = input.format,
            recordingSpeed = input.recordingSpeed,
            country = input.country?.trim()?.takeIf { it.isNotEmpty() },
            purchasePrice = input.purchasePrice,
            rating = input.rating,
            coverPath = input.coverPath,
            coverRemoteUrl = input.coverRemoteUrl,
            barcode = barcode,
            isbn = isbn,
            discogsId = input.discogsId,
            status = input.status,
            extras = input.extras,
            createdAt = now,
            updatedAt = now
        )
        repository.upsert(record)
        return record
    }

    private companion object {
        val RATING_RANGE = 0..5
    }
}
