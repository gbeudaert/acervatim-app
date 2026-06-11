package fr.beudaert.acervatim.domain.usecase

import fr.beudaert.acervatim.domain.model.Artist
import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.normalizeIsbn
import fr.beudaert.acervatim.domain.repository.RecordRepository
import java.time.Clock
import javax.inject.Inject

class UpdateRecordUseCase @Inject constructor(
    private val repository: RecordRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(existing: Record, input: RecordInput): Record {
        val cleanedArtists = input.artists.map(String::trim).filter { it.isNotEmpty() }.distinct()
        require(cleanedArtists.isNotEmpty()) { "at least one artist is required" }
        require(input.title.isNotBlank()) { "title must not be blank" }
        input.rating?.let { require(it in RATING_RANGE) { "rating must be in $RATING_RANGE" } }

        val barcode = input.barcode?.trim()?.takeIf { it.isNotEmpty() }
        val isbn = normalizeIsbn(input.isbn)

        // Pre-check unicité : on tolère un match sur l'existant lui-même (sinon on bloquerait
        // toute édition sans changement du code-barres/ISBN).
        if (barcode != null && barcode != existing.barcode) {
            repository.findByBarcode(barcode)?.let { other ->
                if (other.id != existing.id) throw DuplicateRecordException(
                    existingRecordId = other.id,
                    field = DuplicateRecordException.Field.BARCODE,
                    message = "Barcode $barcode already exists (id=${other.id})"
                )
            }
        }
        if (isbn != null && isbn != existing.isbn) {
            repository.findByIsbn(isbn)?.let { other ->
                if (other.id != existing.id) throw DuplicateRecordException(
                    existingRecordId = other.id,
                    field = DuplicateRecordException.Field.ISBN,
                    message = "ISBN $isbn already exists (id=${other.id})"
                )
            }
        }

        val updated = existing.copy(
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
            updatedAt = clock.instant()
        )
        repository.upsert(updated)
        return updated
    }

    private companion object {
        val RATING_RANGE = 0..5
    }
}
