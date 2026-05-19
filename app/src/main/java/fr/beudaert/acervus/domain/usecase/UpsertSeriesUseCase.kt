package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.repository.SeriesRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Crée ou met à jour une [Series]. Pour une création, `existing` est null et un id est généré.
 */
class UpsertSeriesUseCase @Inject constructor(
    private val repository: SeriesRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(input: SeriesInput, existing: Series? = null): Series {
        require(input.title.isNotBlank()) { "series title must not be blank" }
        input.totalVolumes?.let { require(it >= 0) { "totalVolumes must be >= 0" } }

        val now: Instant = clock.instant()
        val series = Series(
            id = existing?.id ?: UUID.randomUUID().toString(),
            collectionType = existing?.collectionType ?: input.collectionType,
            title = input.title.trim(),
            originalAuthor = input.originalAuthor?.trim()?.takeIf { it.isNotEmpty() },
            publicationStatus = input.publicationStatus?.trim()?.takeIf { it.isNotEmpty() },
            totalVolumes = input.totalVolumes,
            synopsis = input.synopsis?.trim()?.takeIf { it.isNotEmpty() },
            coverPath = input.coverPath,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        repository.upsert(series)
        return series
    }
}

data class SeriesInput(
    val title: String,
    val collectionType: CollectionType,
    val originalAuthor: String? = null,
    val publicationStatus: String? = null,
    val totalVolumes: Int? = null,
    val synopsis: String? = null,
    val coverPath: String? = null
)
