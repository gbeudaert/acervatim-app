package app.acervatim.domain.usecase

import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.normalizeIsbn
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.repository.ExternalSearchRepository
import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.repository.SeriesRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Importe une série manga (et ses tomes) à partir d'un tome scanné enrichi ([ExternalMatch]).
 * Point d'entrée unique des deux parcours décrits dans `docs/interne/spec-scan-import-serie.md` :
 *
 * - [importFromList] : scan depuis la **liste des séries**. Si `importAllKnownTomes`, on énumère
 *   l'édition via le backend (`edition-mapping`) et on crée tous les tomes connus ; sinon on ne crée
 *   que le tome scanné. La série est créée si elle n'existe pas (clé naturelle).
 * - [importTomeIntoSeries] : scan depuis le **détail d'une série existante**. On n'ajoute que le tome
 *   scanné, rattaché à `seriesId`.
 *
 * Statuts : le tome scanné est toujours **OWNED** (même s'il existait déjà en Wishlist) ; les autres
 * tomes connus sont créés en **WISHLIST**. La déduplication par ISBN est silencieuse (aucune
 * exception, contrairement à [AddRecordUseCase]) : un ré-import n'ajoute rien.
 */
class ImportScannedSeriesUseCase @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val recordRepository: RecordRepository,
    private val externalSearch: ExternalSearchRepository,
    private val coverStorage: CoverStorage,
    private val clock: Clock
) {

    suspend fun importFromList(
        scannedIsbn: String,
        match: ExternalMatch,
        importAllKnownTomes: Boolean
    ): ImportResult {
        val normalizedScanned = normalizeIsbn(scannedIsbn)

        // `edition-mapping` n'est tenté que sur demande explicite ET si on a un titre de série à
        // interroger. Un échec dégrade proprement vers « tome scanné seul » (partial=true).
        val mapping: EditionMapping? =
            if (importAllKnownTomes && !match.seriesTitleFr.isNullOrBlank()) {
                externalSearch.editionMapping(match.seriesTitleFr, match.edition).getOrNull()
            } else {
                null
            }
        val degraded = importAllKnownTomes && mapping == null

        val series = resolveOrCreateSeries(match, totalVolumes = mapping?.tomeCount ?: match.totalVolumes)
        val targets = buildTargets(match, scannedIsbn, mapping)
        val outcome = importTomes(series, match, normalizedScanned, targets)

        return ImportResult(
            seriesId = series.id,
            createdVolumes = outcome.created,
            skippedExisting = outcome.skipped,
            partial = (mapping?.ongoing == true) || degraded,
            degradedFullImport = degraded
        )
    }

    suspend fun importTomeIntoSeries(
        seriesId: String,
        scannedIsbn: String,
        match: ExternalMatch
    ): ImportResult {
        val normalizedScanned = normalizeIsbn(scannedIsbn)
        // La série existe déjà (garanti par l'appelant) : seul son id est requis pour rattacher le tome.
        val target = TomeTarget(
            isbn = scannedIsbn,
            volumeNumber = match.volumeNumber,
            titleFr = match.title,
            sourceVolumeRange = match.sourceVolumeRange
        )
        val outcome = importTomes(
            seriesId = seriesId,
            seriesTitle = match.seriesTitleFr ?: match.title,
            match = match,
            normalizedScanned = normalizedScanned,
            targets = listOf(target)
        )
        return ImportResult(
            seriesId = seriesId,
            createdVolumes = outcome.created,
            skippedExisting = outcome.skipped,
            partial = false
        )
    }

    private suspend fun resolveOrCreateSeries(match: ExternalMatch, totalVolumes: Int?): Series {
        val title = (match.seriesTitleFr ?: match.title).trim()
        val author = match.creators.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        seriesRepository.findByNaturalKey(CollectionType.MANGA, title, author)?.let { return it }

        val now = clock.instant()
        // Une source est connue (jaquette du tome scanné) : on la met en cache local pour le header
        // de série, dès la création. Best-effort — un échec réseau ne bloque pas l'import.
        val seriesId = UUID.randomUUID().toString()
        val series = Series(
            id = seriesId,
            collectionType = CollectionType.MANGA,
            title = title,
            originalAuthor = author,
            publicationStatus = null,
            totalVolumes = totalVolumes,
            synopsis = match.description?.trim()?.takeIf { it.isNotEmpty() },
            coverPath = cacheCover(seriesId, match.coverUrl),
            createdAt = now,
            updatedAt = now
        )
        seriesRepository.upsert(series)
        return series
    }

    /** Tomes à importer : ceux de l'édition (si mapping) + garantie de présence du tome scanné. */
    private fun buildTargets(
        match: ExternalMatch,
        scannedIsbn: String,
        mapping: EditionMapping?
    ): List<TomeTarget> {
        val scannedTarget = TomeTarget(
            isbn = scannedIsbn,
            volumeNumber = match.volumeNumber,
            titleFr = match.title,
            sourceVolumeRange = match.sourceVolumeRange
        )
        if (mapping == null) {
            return listOf(scannedTarget)
        }
        val normalizedScanned = normalizeIsbn(scannedIsbn)
        val tomes = mapping.tomes.map {
            TomeTarget(
                isbn = it.isbn,
                volumeNumber = it.editionVolume,
                titleFr = it.titleFr,
                sourceVolumeRange = it.sourceVolumeRange
            )
        }
        // Le tome scanné peut ne pas figurer dans l'énumération BnF : on l'ajoute pour le garantir.
        val scannedPresent = tomes.any { normalizeIsbn(it.isbn) == normalizedScanned }
        return if (scannedPresent) tomes else tomes + scannedTarget
    }

    private suspend fun importTomes(
        series: Series,
        match: ExternalMatch,
        normalizedScanned: String?,
        targets: List<TomeTarget>
    ): Outcome = importTomes(series.id, series.title, match, normalizedScanned, targets)

    private suspend fun importTomes(
        seriesId: String,
        seriesTitle: String,
        match: ExternalMatch,
        normalizedScanned: String?,
        targets: List<TomeTarget>
    ): Outcome {
        val author = match.creators.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_AUTHOR
        val now = clock.instant()
        var created = 0
        var skipped = 0

        for (target in targets) {
            val isbn = normalizeIsbn(target.isbn)
            val isScanned = isbn != null && isbn == normalizedScanned

            if (isbn != null && recordRepository.findByIsbn(isbn) != null) {
                skipped++
                continue
            }

            // Seul le tome scanné porte une jaquette connue (`match.coverUrl`) ; les tomes de
            // l'édition énumérée n'en ont pas. On conserve l'URL distante (re-cache ultérieur) et
            // on télécharge en best-effort.
            val recordId = UUID.randomUUID().toString()
            val coverRemoteUrl = if (isScanned) match.coverUrl?.trim()?.takeIf { it.isNotEmpty() } else null
            val record = Record(
                id = recordId,
                collectionType = CollectionType.MANGA,
                seriesId = seriesId,
                volumeNumber = target.volumeNumber,
                artists = listOf(Artist(id = "", name = author)),
                title = tomeTitle(target, seriesTitle),
                isbn = isbn,
                coverPath = cacheCover(recordId, coverRemoteUrl),
                coverRemoteUrl = coverRemoteUrl,
                status = if (isScanned) ItemStatus.OWNED else ItemStatus.WISHLIST,
                extras = buildTomeExtras(author, match, target),
                createdAt = now,
                updatedAt = now
            )
            recordRepository.upsert(record)
            created++
        }

        // Garantie : le tome scanné est OWNED, même s'il existait déjà (ex. en Wishlist).
        if (normalizedScanned != null) {
            recordRepository.findByIsbn(normalizedScanned)?.let { existing ->
                if (existing.status != ItemStatus.OWNED) {
                    recordRepository.updateStatus(existing.id, ItemStatus.OWNED, now)
                }
            }
        }

        return Outcome(created = created, skipped = skipped)
    }

    /**
     * Télécharge et met en cache local la jaquette référencée par [url] pour l'entité [id].
     * Best-effort : renvoie le chemin local, ou `null` si l'URL est absente/vide ou si le
     * téléchargement échoue (réseau) — l'import ne doit jamais échouer sur une jaquette.
     */
    private suspend fun cacheCover(id: String, url: String?): String? {
        val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { coverStorage.saveFromUrl(id, cleanUrl) }.getOrNull()
    }

    private fun tomeTitle(target: TomeTarget, seriesTitle: String): String {
        target.titleFr?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val vol = target.volumeNumber
        return if (vol != null) "$seriesTitle T.$vol" else seriesTitle
    }

    /** Extras manga d'un tome : auteur + (édition / plage source / éditeur) quand connus. */
    private fun buildTomeExtras(
        author: String,
        match: ExternalMatch,
        target: TomeTarget
    ): Map<String, String> = buildMap {
        put(MangaExtras.KEY_ORIGINAL_AUTHOR, author)
        match.edition?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { put(MangaExtras.KEY_EDITION, it) }
        match.publisherFr?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { put(MangaExtras.KEY_PUBLISHER_FR, it) }
        target.sourceVolumeRange?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { put(MangaExtras.KEY_SOURCE_VOLUME_RANGE, it) }
    }

    private data class TomeTarget(
        val isbn: String?,
        val volumeNumber: Int?,
        val titleFr: String?,
        val sourceVolumeRange: String? = null
    )

    private data class Outcome(val created: Int, val skipped: Int)

    private companion object {
        const val DEFAULT_AUTHOR = "Inconnu"
    }
}

/** Bilan d'un import de série. */
data class ImportResult(
    val seriesId: String,
    /** Nombre de tomes effectivement créés. */
    val createdVolumes: Int,
    /** Tomes ignorés car déjà présents (dédup ISBN). */
    val skippedExisting: Int,
    /** Édition en cours OU énumération indisponible → la série peut être incomplète. */
    val partial: Boolean,
    /** Import complet demandé mais `edition-mapping` indisponible → repli sur le tome scanné seul. */
    val degradedFullImport: Boolean = false
)
