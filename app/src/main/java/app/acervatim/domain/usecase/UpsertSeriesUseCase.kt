package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.SeriesRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Signale qu'une autre [Series] possède déjà la même clé naturelle
 * (`collectionType, title normalisé, originalAuthor normalisé`).
 * Le caller (ViewModel) est responsable de proposer un message + offrir « ouvrir l'existante ».
 */
class DuplicateSeriesException(
    val existingSeriesId: String,
    val title: String,
    val originalAuthor: String?,
    message: String
) : Exception(message)

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

        val title = input.title.trim()
        val author = input.originalAuthor?.trim()?.takeIf { it.isNotEmpty() }
        val collectionType = existing?.collectionType ?: input.collectionType

        // Pré-check unicité : on tolère un match sur soi-même (édition sans changement de clé).
        val keyChanged = existing == null ||
            existing.title != title ||
            existing.originalAuthor != author ||
            existing.collectionType != collectionType
        // L'édition (éditeur + mention) n'est pas saisissable dans le formulaire : elle vient d'un
        // import et se conserve telle quelle à l'édition — sinon modifier un synopsis ferait
        // retomber la série dans la clé « sans édition » et la confondrait avec une autre.
        if (keyChanged) {
            repository.findByNaturalKey(
                collectionType,
                title,
                author,
                publisherFr = existing?.publisherFr,
                edition = existing?.edition
            )?.let { other ->
                if (other.id != existing?.id) throw DuplicateSeriesException(
                    existingSeriesId = other.id,
                    title = title,
                    originalAuthor = author,
                    message = "Series '$title' (author=$author, type=$collectionType) already exists (id=${other.id})"
                )
            }
        }

        val now: Instant = clock.instant()
        val series = Series(
            id = existing?.id ?: UUID.randomUUID().toString(),
            collectionType = collectionType,
            title = title,
            originalAuthor = author,
            publicationStatus = input.publicationStatus?.trim()?.takeIf { it.isNotEmpty() },
            totalVolumes = input.totalVolumes,
            synopsis = input.synopsis?.trim()?.takeIf { it.isNotEmpty() },
            coverPath = input.coverPath,
            edition = existing?.edition,
            publisherFr = existing?.publisherFr,
            mangaId = existing?.mangaId,
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
