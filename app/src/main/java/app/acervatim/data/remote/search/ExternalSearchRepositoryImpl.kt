package app.acervatim.data.remote.search

import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.repository.ExternalSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation backend de [ExternalSearchRepository] : appelle `GET /v1/search` et projette les
 * `UnifiedItemDto` en [ExternalMatch] domaine. Les erreurs réseau / `ApiException` (dont `402` quota)
 * sont capturées en `Result.failure` pour que l'UI retombe proprement sur la saisie manuelle.
 */
@Singleton
class ExternalSearchRepositoryImpl @Inject constructor(
    private val api: AcervatimApi
) : ExternalSearchRepository {

    override suspend fun searchByBarcode(barcode: String, type: String): Result<List<ExternalMatch>> =
        runCatching {
            // Recherche par code : on envoie `barcode` (XOR avec `q`), jamais les deux.
            api.search(type = type, barcode = barcode, limit = SEARCH_LIMIT)
                .data
                .map { it.toExternalMatch() }
        }

    private companion object {
        const val SEARCH_LIMIT = 10
    }
}

internal fun UnifiedItemDto.toExternalMatch(): ExternalMatch = ExternalMatch(
    source = source,
    sourceId = sourceId,
    title = title,
    creators = creators,
    year = releaseDate?.take(4)?.toIntOrNull(),
    coverUrl = coverUrl,
    description = description
)
