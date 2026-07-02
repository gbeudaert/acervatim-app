package app.acervatim.data.remote.search

import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.EditionMappingDto
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.repository.ExternalSearchRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

    override suspend fun editionMapping(titleFr: String, edition: String?): Result<EditionMapping> =
        runCatching {
            api.editionMapping(title = titleFr, edition = edition?.takeIf { it.isNotBlank() })
                .toDomain()
        }

    private companion object {
        const val SEARCH_LIMIT = 10
    }
}

internal fun UnifiedItemDto.toExternalMatch(): ExternalMatch {
    // Données spécifiques au pivot manga BnF→MAL (cf. mal.adapter.ts) ; absentes pour le vinyle.
    val scannedTome = metadata?.get("scannedTome") as? JsonObject
    return ExternalMatch(
        source = source,
        sourceId = sourceId,
        title = title,
        creators = creators,
        year = releaseDate?.take(4)?.toIntOrNull(),
        coverUrl = coverUrl,
        description = description,
        // Titre de la série (461$t BnF) pour l'énumération des tomes ; repli sur le
        // titre du tome si absent (ancien comportement / éditeurs sans lien d'ensemble).
        seriesTitleFr = scannedTome?.stringField("seriesTitleFr")
            ?: scannedTome?.stringField("titleFr"),
        volumeNumber = scannedTome?.intField("volume"),
        edition = scannedTome?.stringField("edition"),
        publisherFr = scannedTome?.stringField("publisherFr"),
        sourceVolumeRange = scannedTome?.stringField("sourceVolumeRange"),
        totalVolumes = metadata?.intField("num_volumes")
    )
}

internal fun EditionMappingDto.toDomain(): EditionMapping = EditionMapping(
    titleFr = titleFr,
    edition = edition,
    tomeCount = tomeCount,
    tomes = tomes.map {
        EditionTome(
            editionVolume = it.editionVolume,
            isbn = it.isbn,
            titleFr = it.titleFr,
            sourceVolumeRange = it.sourceVolumeRange
        )
    },
    ongoing = ongoing
)

private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.intField(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull
