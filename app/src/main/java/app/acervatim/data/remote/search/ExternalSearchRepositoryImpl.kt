package app.acervatim.data.remote.search

import app.acervatim.data.remote.api.AcervatimApi
import app.acervatim.data.remote.dto.CreateImportJobRequest
import app.acervatim.data.remote.dto.EditionMappingDto
import app.acervatim.data.remote.dto.ImportJobDto
import app.acervatim.data.remote.dto.ImportJobStatusDto
import app.acervatim.data.remote.dto.SeriesEditionDto
import app.acervatim.data.remote.dto.SeriesUpdateDto
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.data.remote.error.ApiException
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.ImportJob
import app.acervatim.domain.model.ImportJobProgress
import app.acervatim.domain.model.ImportJobState
import app.acervatim.domain.model.ImportPhase
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.model.SeriesEditionOption
import app.acervatim.domain.model.SeriesUpdate
import app.acervatim.domain.repository.ExternalSearchRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    override suspend fun searchByText(query: String, type: String): Result<List<ExternalMatch>> =
        runCatching {
            // Recherche texte : on envoie `q` (XOR avec `barcode`), jamais les deux.
            // Limite plus haute qu'au scan : l'utilisateur choisit ici parmi les pressages, il lui
            // en faut assez pour que le bon soit dans la liste.
            api.search(type = type, query = query, limit = TEXT_SEARCH_LIMIT)
                .data
                .map { it.toExternalMatch() }
        }.recoverCatching { throw it.toExternalSearchException() }

    override suspend fun searchMangaSeries(query: String): Result<List<SeriesCandidate>> =
        runCatching {
            // Même route que la recherche texte vinyle : c'est le `type` qui route vers MangaDex
            // côté serveur. Les résultats sont des séries, d'où une projection dédiée.
            api.search(type = MANGA_TYPE, query = query, limit = TEXT_SEARCH_LIMIT)
                .data
                .map { it.toSeriesCandidate() }
        }.recoverCatching { throw it.toExternalSearchException() }

    override suspend fun listEditions(
        mangaId: String,
        title: String?
    ): Result<List<SeriesEditionOption>> =
        runCatching {
            val response = api.searchEditions(mangaId = mangaId, title = title?.takeIf { it.isNotBlank() })
            // Liste vide ET incomplète : la BnF a lâché en route, ce n'est pas « aucune édition ».
            // Le dire comme une panne réseau (retentable) — sinon l'écran proposerait la création
            // manuelle d'une série que la BnF connaît peut-être. Une liste incomplète mais non vide
            // reste montrée : elle contient déjà de quoi choisir.
            if (!response.complete && response.editions.isEmpty()) {
                throw ExternalSearchException(ExternalSearchFailure.NETWORK)
            }
            response.editions.map { it.toDomain() }
        }.recoverCatching { throw it.toExternalSearchException() }

    override suspend fun editionMapping(
        titleFr: String,
        edition: String?,
        publisher: String?,
        mangaId: String?,
        since: LocalDate?
    ): Result<EditionMapping> =
        runCatching {
            api.editionMapping(
                title = titleFr,
                edition = edition?.takeIf { it.isNotBlank() },
                publisher = publisher?.takeIf { it.isNotBlank() },
                mangaId = mangaId?.takeIf { it.isNotBlank() },
                // Format strict `AAAAMMJJ` : la BnF ignore en silence un `AAAA-MM-JJ` (0 notice,
                // aucune erreur) — c'est le serveur qui le transmet tel quel au SRU.
                since = since?.format(DateTimeFormatter.BASIC_ISO_DATE)
            ).toDomain()
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

    override suspend fun startImportJob(
        titleFr: String,
        edition: String?,
        publisher: String?,
        mangaId: String?
    ): Result<ImportJob> =
        runCatching {
            api.startImportJob(
                CreateImportJobRequest(
                    title = titleFr,
                    edition = edition?.takeIf { it.isNotBlank() },
                    publisher = publisher?.takeIf { it.isNotBlank() },
                    mangaId = mangaId?.takeIf { it.isNotBlank() }
                )
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

    override suspend fun seriesUpdates(): Result<List<SeriesUpdate>> =
        runCatching { api.seriesUpdates().updates.map { it.toDomain() } }
            .recoverCatching { throw it.toExternalSearchException() }

    private companion object {
        const val SEARCH_LIMIT = 10
        const val TEXT_SEARCH_LIMIT = 25

        /** Code de collection du manga sur le contrat (`CollectionType.MANGA.typeCode`). */
        const val MANGA_TYPE = "manga"
    }
}

/**
 * Projette un résultat de `GET /v1/search?type=manga&q=` en candidat de série. Le nominal est
 * MangaDex (`metadata.mangaId`, `titleFr`, `titleRomaji`…) ; le repli MAL ne porte que ses propres
 * champs (`num_volumes`, statut MAL), d'où les replis ci-dessous plutôt qu'un échec.
 */
internal fun UnifiedItemDto.toSeriesCandidate(): SeriesCandidate {
    val meta = metadata
    return SeriesCandidate(
        source = source,
        sourceId = sourceId,
        // `search/editions` refuse (`400`) tout id qui n'est pas un UUID MangaDex : on ne propage
        // qu'un id valide, pour que l'écran sache d'avance qu'un candidat n'a pas d'éditions à lister.
        mangaId = (meta?.stringField("mangaId") ?: sourceId.takeIf { source == SOURCE_MANGADEX })
            ?.takeIf { UUID_PATTERN.matches(it) },
        title = title,
        titleFr = meta?.stringField("titleFr"),
        titleRomaji = meta?.stringField("titleRomaji"),
        authors = creators,
        year = meta?.intField("year") ?: releaseDate?.take(4)?.toIntOrNull(),
        coverUrl = coverUrl,
        synopsis = description,
        status = meta?.stringField("status")?.let(::normalizeSeriesStatus),
        volumeCount = meta?.intField("num_volumes")?.takeIf { it > 0 },
        malId = meta?.stringField("malId") ?: sourceId.takeIf { source == SOURCE_MAL },
        anilistId = meta?.stringField("anilistId")
    )
}

/**
 * Ramène le statut au vocabulaire MangaDex (`ongoing`, `completed`, `hiatus`, `cancelled`) : le
 * repli MAL parle le sien (`currently_publishing`, `finished`…), et l'écran n'a pas à connaître la
 * source pour l'afficher.
 */
private fun normalizeSeriesStatus(raw: String): String = when (raw) {
    "currently_publishing" -> "ongoing"
    "finished" -> "completed"
    "on_hiatus" -> "hiatus"
    "discontinued" -> "cancelled"
    else -> raw
}

internal fun SeriesEditionDto.toDomain(): SeriesEditionOption = SeriesEditionOption(
    seriesTitleFr = seriesTitleFr,
    publisher = publisher,
    edition = edition?.takeIf { it.isNotBlank() },
    isArtEdition = isArtEdition,
    tomeCount = tomeCount,
    firstYear = firstYear,
    lastYear = lastYear,
    ongoing = ongoing,
    hasSourceVolumeRange = hasSourceVolumeRange,
    sampleIsbn = sampleIsbn
)

internal fun SeriesUpdateDto.toDomain(): SeriesUpdate = SeriesUpdate(
    nodeId = nodeId,
    title = title,
    edition = edition?.takeIf { it.isNotBlank() },
    publisherFr = publisherFr?.takeIf { it.isNotBlank() },
    newTomes = newTomes.map {
        EditionTome(
            editionVolume = it.editionVolume,
            isbn = it.isbn,
            titleFr = it.titleFr,
            coverUrl = it.coverUrl,
            noticeCreatedAt = parseNoticeDate(it.noticeCreatedAt)
        )
    }
)

/** `YYYY-MM-DD` → [LocalDate] ; `null` si absent ou illisible (jamais d'échec du mapping entier). */
private fun parseNoticeDate(raw: String?): LocalDate? =
    raw?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private const val SOURCE_MANGADEX = "mangadex"
private const val SOURCE_MAL = "mal"

/** Forme d'un id MangaDex, la seule que `GET /v1/search/editions` accepte. */
private val UUID_PATTERN =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

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
        // Discriminants d'un pressage (SD1) : sans eux, deux résultats d'une recherche texte
        // s'affichent en lignes identiques alors qu'ils désignent deux disques différents.
        label = metadata?.stringListField("label")?.firstOrNull(),
        country = metadata?.stringField("country"),
        catalogNumber = metadata?.stringField("catno"),
        barcodes = metadata?.stringListField("barcodes") ?: emptyList(),
        // `master_id` est un nombre chez Discogs : `stringField` en rend le littéral tel quel, ce
        // qui suffit — l'app ne s'en sert que comme clé de regroupement.
        masterId = metadata?.stringField("masterId"),
        // Titre de la série (461$t BnF) pour l'énumération des tomes ; repli sur le
        // titre du tome si absent (ancien comportement / éditeurs sans lien d'ensemble).
        seriesTitleFr = scannedTome?.stringField("seriesTitleFr")
            ?: scannedTome?.stringField("titleFr"),
        volumeNumber = scannedTome?.intField("volume"),
        edition = scannedTome?.stringField("edition"),
        publisherFr = scannedTome?.stringField("publisherFr"),
        sourceVolumeRange = scannedTome?.stringField("sourceVolumeRange"),
        totalVolumes = metadata?.intField("num_volumes"),
        // Identité MangaDex produite au scan : la série la portera, pour joindre ses jaquettes
        // ensuite sans rapprochement par titre.
        mangaId = (metadata?.get("pivot") as? JsonObject)?.stringField("mangaId")
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
            description = it.description,
            noticeCreatedAt = parseNoticeDate(it.noticeCreatedAt)
        )
    },
    ongoing = ongoing,
    publisher = publisher?.takeIf { it.isNotBlank() },
    complete = complete
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

/**
 * Traduit une erreur de transport en [ExternalSearchException] : au-delà de cette frontière, plus
 * personne ne connaît les statuts HTTP. Le cas qui compte est le `403 source-token-required` — le
 * confondre avec une liste vide ferait croire à l'utilisateur que son disque n'existe pas.
 *
 * L'ordre des branches n'est pas indifférent : [ApiException] hérite d'[IOException], les cas
 * spécialisés doivent donc passer avant le repli réseau.
 */
private fun Throwable.toExternalSearchException(): ExternalSearchException = when (this) {
    is ExternalSearchException -> this
    is ApiException.Forbidden ->
        if (problem?.typeSlug == SOURCE_TOKEN_REQUIRED_SLUG) {
            ExternalSearchException(
                failure = ExternalSearchFailure.SOURCE_TOKEN_REQUIRED,
                provider = problem?.provider,
                cause = this
            )
        } else {
            ExternalSearchException(ExternalSearchFailure.UNKNOWN, cause = this)
        }
    is ApiException.TooManyRequests -> ExternalSearchException(ExternalSearchFailure.RATE_LIMITED, cause = this)
    // 502/503 : la source externe (ou l'attente en file) a lâché — c'est passager, comme le réseau.
    is ApiException.Upstream -> ExternalSearchException(ExternalSearchFailure.NETWORK, cause = this)
    is ApiException -> ExternalSearchException(ExternalSearchFailure.UNKNOWN, cause = this)
    is IOException -> ExternalSearchException(ExternalSearchFailure.NETWORK, cause = this)
    else -> ExternalSearchException(ExternalSearchFailure.UNKNOWN, cause = this)
}

/** Slug RFC 9457 du refus de jeton de source (cf. `agent/backend/recherche-textuelle-discogs.md`). */
private const val SOURCE_TOKEN_REQUIRED_SLUG = "source-token-required"
