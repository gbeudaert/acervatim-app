package app.acervatim.data.remote.search

import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.CreateImportJobRequest
import app.acervatim.data.remote.dto.EditionMappingDto
import app.acervatim.data.remote.dto.ImportJobDto
import app.acervatim.data.remote.dto.ImportJobStatusDto
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.data.remote.error.ApiException
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ImportJob
import app.acervatim.domain.model.ImportJobProgress
import app.acervatim.domain.model.ImportJobState
import app.acervatim.domain.model.ImportPhase
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.repository.ExternalSearchRepository
import kotlinx.serialization.json.JsonArray
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

    override suspend fun resolveCover(
        isbn: String,
        seriesTitleFr: String?,
        volumeNumber: Int?,
        edition: String?
    ): Result<String?> =
        runCatching {
            // Le repli `intitle:` serveur n'a de sens qu'avec titre de série + n° de tome : on
            // n'envoie le hint (title/volume/edition) qu'à cette condition, sinon résolution `isbn:` seule.
            val hasHint = !seriesTitleFr.isNullOrBlank() && volumeNumber != null
            api.cover(
                isbn = isbn,
                title = if (hasHint) seriesTitleFr else null,
                volume = if (hasHint) volumeNumber else null,
                edition = if (hasHint) edition?.takeIf { it.isNotBlank() } else null
            ).coverUrl?.takeIf { it.isNotBlank() }
        }

    override suspend fun startImportJob(titleFr: String, edition: String?): Result<ImportJob> =
        runCatching {
            api.startImportJob(
                CreateImportJobRequest(title = titleFr, edition = edition?.takeIf { it.isNotBlank() })
            ).toDomain()
        }

    override suspend fun importJobStatus(jobId: String): Result<ImportJob?> =
        runCatching {
            try {
                api.importJobStatus(jobId).toDomain()
            } catch (notFound: ApiException.NotFound) {
                // Job inconnu (expiré/purgé) : succès explicite `null`, distinct d'une erreur réseau.
                null
            }
        }

    private companion object {
        const val SEARCH_LIMIT = 10
    }
}

internal fun ImportJobDto.toDomain(): ImportJob = ImportJob(
    jobId = jobId,
    state = ImportJobState.fromWire(state)
)

internal fun ImportJobStatusDto.toDomain(): ImportJob = ImportJob(
    jobId = jobId,
    state = ImportJobState.fromWire(state),
    progress = progress?.let {
        ImportJobProgress(
            phase = ImportPhase.fromWire(it.phase),
            done = it.done,
            total = it.total
        )
    }
)

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
        // Champs vinyle issus de `metadata` Discogs (recherche) ; restent `null`/vides pour le manga.
        genre = metadata?.stringListField("genres")?.firstOrNull(),
        format = metadata?.stringListField("format")?.let(::deriveVinylFormat),
        recordingSpeed = metadata?.stringField("recordingSpeed")
            ?.let { code -> runCatching { RecordingSpeed.valueOf(code) }.getOrNull() },
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
            sourceVolumeRange = it.sourceVolumeRange,
            coverUrl = it.coverUrl,
            description = it.description
        )
    },
    ongoing = ongoing
)

private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.intField(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.stringListField(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
        ?: emptyList()

/**
 * Dérive le format physique app depuis le tableau `format` brut de Discogs (recherche), qui mêle le
 * nom du format et ses descriptions (ex. `["Vinyl", "LP", "Album", "33 ⅓ RPM"]`). Renvoie `null` si
 * non concluant : mieux vaut laisser l'utilisateur choisir que deviner à tort.
 */
private fun deriveVinylFormat(tokens: List<String>): AcervatimFormat? {
    val set = tokens.map { it.trim().lowercase() }
    return when {
        set.contains("lp") -> AcervatimFormat.LP_33
        set.contains("single") || set.contains("7\"") -> AcervatimFormat.SINGLE_45
        set.any { it.contains("shellac") } -> AcervatimFormat.SHELLAC_78
        set.any { it.contains("vinyl") } -> AcervatimFormat.OTHER
        else -> null
    }
}
