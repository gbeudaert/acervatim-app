package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.repository.RecordRepository
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

        val updated = existing.copy(
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
            barcode = input.barcode?.trim()?.takeIf { it.isNotEmpty() },
            discogsId = input.discogsId,
            status = input.status,
            updatedAt = clock.instant()
        )
        repository.upsert(updated)
        return updated
    }

    private companion object {
        val RATING_RANGE = 0..5
    }
}
