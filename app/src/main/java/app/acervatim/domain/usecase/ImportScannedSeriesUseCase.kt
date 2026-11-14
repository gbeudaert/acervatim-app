package app.acervatim.domain.usecase

import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.model.SeriesEditionOption
import app.acervatim.domain.model.normalizeEditionKey
import app.acervatim.domain.model.normalizeIsbn
import app.acervatim.domain.model.normalizePublisherKey
import app.acervatim.domain.model.readString
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.repository.ExternalSearchRepository
import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Importe une série manga (et ses tomes). Point d'entrée unique des parcours décrits dans
 * `docs/interne/spec-scan-import-serie.md` et `spec-macro-import-serie-manga.md` :
 *
 * - [importFromList] : scan depuis la **liste des séries**. Si `importAllKnownTomes`, on énumère
 *   l'édition via le backend (`edition-mapping`) et on crée tous les tomes connus ; sinon on ne crée
 *   que le tome scanné. La série est créée si elle n'existe pas (clé naturelle).
 * - [importTomeIntoSeries] : scan depuis le **détail d'une série existante**. On n'ajoute que le tome
 *   scanné, rattaché à `seriesId`.
 * - [importFromSearch] : série trouvée **par titre**, sans aucun tome possédé. On énumère l'édition
 *   choisie et on crée tous ses tomes en wishlist — le comportement nominal de l'import, privé
 *   jusqu'ici de sa porte d'entrée.
 *
 * Statuts : le tome scanné est toujours **OWNED** (même s'il existait déjà en Wishlist) ; les autres
 * tomes connus sont créés en **WISHLIST**. La déduplication par ISBN est silencieuse (aucune
 * exception, contrairement à [AddRecordUseCase]) : un ré-import n'ajoute rien.
 *
 * L'édition (éditeur + mention) fait partie de l'identité de la série : deux éditions d'une même
 * œuvre donnent deux séries, ce qui évite aussi le `409 (série + volume)` qui faisait échouer la
 * synchro quand deux « tome 1 » se retrouvaient sous le même nœud.
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
                externalSearch.editionMapping(
                    titleFr = match.seriesTitleFr,
                    edition = match.edition,
                    publisher = match.publisherFr,
                    mangaId = match.mangaId
                ).getOrNull()
            } else {
                null
            }
        val degraded = importAllKnownTomes && mapping == null

        val context = TomeContext.of(match)
        val series = resolveOrCreateSeries(
            SeriesDraft(
                title = (match.seriesTitleFr ?: match.title).trim(),
                author = context.author,
                publisherFr = context.publisherFr,
                edition = context.edition,
                mangaId = match.mangaId,
                synopsis = match.description,
                coverUrl = match.coverUrl,
                totalVolumes = mapping?.tomeCount ?: match.totalVolumes
            )
        )
        val targets = buildTargets(match, scannedIsbn, mapping)
        val outcome = importTomes(series.id, series.title, context, normalizedScanned, targets)

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
        val outcome = importTomes(
            seriesId = seriesId,
            seriesTitle = match.seriesTitleFr ?: match.title,
            context = TomeContext.of(match),
            normalizedScanned = normalizedScanned,
            targets = listOf(scannedTarget(match, scannedIsbn))
        )
        return ImportResult(
            seriesId = seriesId,
            createdVolumes = outcome.created,
            skippedExisting = outcome.skipped,
            partial = false
        )
    }

    /**
     * Importe une série **trouvée par titre** (candidat MangaDex + édition BnF choisis par
     * l'utilisateur) : la série est créée avec son identité d'édition, puis **tous** les tomes de
     * l'édition sont créés en [ItemStatus.WISHLIST] — aucun tome n'est possédé, c'est le besoin
     * wishlist (« je cherche *Frieren* pour la noter »).
     *
     * Les paramètres d'`edition-mapping` sont repris **tels quels** de [edition] (titre de série
     * catalogué, éditeur, mention) : ce sont eux que le serveur a groupés, les retoucher ferait
     * manquer l'édition. Un mapping indisponible (réseau, BnF) crée quand même la série, vide, et le
     * signale par [ImportResult.degradedFullImport] : l'utilisateur garde sa série en wishlist et
     * peut relancer.
     */
    suspend fun importFromSearch(
        candidate: SeriesCandidate,
        edition: SeriesEditionOption
    ): ImportResult {
        val mapping = externalSearch.editionMapping(
            titleFr = edition.seriesTitleFr,
            edition = edition.edition,
            publisher = edition.publisher,
            mangaId = candidate.mangaId
        ).getOrNull()

        // Un seul libellé d'éditeur pour la série et ses tomes : celui de l'édition choisie, déjà
        // celui que le serveur a groupé (la clé normalisée le rapproche de toute autre graphie).
        // Groupe sans éditeur connu : celui que le serveur a retenu pour l'énumération (dominant),
        // s'il en a retenu un — sinon la série reste sans éditeur.
        val context = TomeContext(
            author = candidate.authors.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            publisherFr = (edition.publisher ?: mapping?.publisher)?.trim()?.takeIf { it.isNotEmpty() },
            edition = edition.edition?.trim()?.takeIf { it.isNotEmpty() }
        )
        val series = resolveOrCreateSeries(
            SeriesDraft(
                title = edition.seriesTitleFr.trim(),
                author = context.author,
                publisherFr = context.publisherFr,
                edition = context.edition,
                mangaId = candidate.mangaId,
                synopsis = candidate.synopsis,
                coverUrl = candidate.coverUrl,
                totalVolumes = mapping?.tomeCount ?: edition.tomeCount,
                publicationStatus = publicationStatusOf(candidate, edition, mapping)
            )
        )
        val targets = mapping?.tomes.orEmpty().map { it.toTarget(coverUrl = it.coverUrl) }
        val outcome = importTomes(series.id, series.title, context, normalizedScanned = null, targets)

        return ImportResult(
            seriesId = series.id,
            createdVolumes = outcome.created,
            skippedExisting = outcome.skipped,
            // Un mapping vide n'est pas une panne (le serveur a répondu), mais la série n'a reçu
            // aucun tome : on la signale partielle — un éditeur explicite sans correspondance donne
            // `tomes: []` côté serveur, sans repli sur l'éditeur dominant.
            partial = mapping == null || mapping.tomes.isEmpty() || mapping.ongoing || edition.ongoing,
            degradedFullImport = mapping == null
        )
    }

    /**
     * Ajoute à une série **existante** des tomes de son édition parus depuis l'import (rattrapage,
     * AD3), tous en [ItemStatus.WISHLIST] : l'utilisateur ne les possède pas, il vient d'apprendre
     * qu'ils existent. Mêmes règles que l'import : dédup ISBN silencieuse, jaquette connue sinon
     * résolue par ISBN, éditeur et mention de la série reportés sur chaque tome.
     */
    suspend fun importNewTomes(series: Series, tomes: List<EditionTome>): ImportResult {
        val context = TomeContext(
            author = series.originalAuthor?.trim()?.takeIf { it.isNotEmpty() },
            publisherFr = series.publisherFr?.trim()?.takeIf { it.isNotEmpty() },
            edition = series.edition?.trim()?.takeIf { it.isNotEmpty() }
        )
        val outcome = importTomes(
            seriesId = series.id,
            seriesTitle = series.title,
            context = context,
            normalizedScanned = null,
            targets = tomes.map { it.toTarget(coverUrl = it.coverUrl) }
        )
        return ImportResult(
            seriesId = series.id,
            createdVolumes = outcome.created,
            skippedExisting = outcome.skipped,
            partial = false
        )
    }

    /**
     * Retrouve la série par sa clé naturelle **avec** l'édition, sinon la crée.
     *
     * Repli de transition : une série créée avant la v8 n'a ni éditeur ni mention (clé `''`). Si
     * elle existe pour ce titre + auteur **et qu'elle est compatible** avec l'édition importée (cf.
     * [isCompatibleLegacy]), on l'**adopte** en lui donnant cette édition, au lieu de créer une
     * seconde série à côté de celle que l'utilisateur possède déjà.
     */
    private suspend fun resolveOrCreateSeries(draft: SeriesDraft): Series {
        seriesRepository.findByNaturalKey(
            CollectionType.MANGA,
            draft.title,
            draft.author,
            publisherFr = draft.publisherFr,
            edition = draft.edition
        )?.let { return it }

        if (draft.publisherFr != null || draft.edition != null) {
            seriesRepository.findByNaturalKey(CollectionType.MANGA, draft.title, draft.author)
                ?.takeIf { it.publisherFr == null && it.edition == null }
                ?.takeIf { isCompatibleLegacy(it, draft) }
                ?.let { legacy ->
                    val adopted = legacy.copy(
                        publisherFr = draft.publisherFr,
                        edition = draft.edition,
                        mangaId = legacy.mangaId ?: draft.mangaId,
                        updatedAt = clock.instant()
                    )
                    seriesRepository.upsert(adopted)
                    return adopted
                }
        }

        val now = clock.instant()
        // Une source est connue (jaquette du tome scanné ou de l'œuvre) : on la met en cache local
        // pour le header de série, dès la création. Best-effort — un échec réseau ne bloque pas.
        val seriesId = UUID.randomUUID().toString()
        val series = Series(
            id = seriesId,
            collectionType = CollectionType.MANGA,
            title = draft.title,
            originalAuthor = draft.author,
            publicationStatus = draft.publicationStatus,
            totalVolumes = draft.totalVolumes,
            synopsis = draft.synopsis?.trim()?.takeIf { it.isNotEmpty() },
            coverPath = cacheCover(seriesId, draft.coverUrl),
            edition = draft.edition,
            publisherFr = draft.publisherFr,
            mangaId = draft.mangaId,
            createdAt = now,
            updatedAt = now
        )
        seriesRepository.upsert(series)
        return series
    }

    /**
     * Une série d'avant la v8 peut-elle recevoir l'édition de [draft] ? Avant la v8, toutes les
     * éditions d'un même titre tombaient dans la même série : l'adopter aveuglément y rangerait une
     * 2-en-1 France Loisirs à côté de tomes Pika — exactement la confusion que la v8 corrige. On ne
     * l'adopte donc que si rien ne s'y oppose : **aucun tome**, ou des tomes dont l'éditeur (extras)
     * est celui de l'import **ou inconnu**, et dont la mention d'édition est celle de l'import **ou
     * absente**. Un seul tome d'un autre éditeur ou d'une autre édition suffit à créer une série neuve.
     * Les comparaisons passent par les clés normalisées, comme la clé naturelle.
     */
    private suspend fun isCompatibleLegacy(legacy: Series, draft: SeriesDraft): Boolean {
        val tomes = recordRepository.observeBySeriesId(legacy.id).first()
        val wantPublisher = normalizePublisherKey(draft.publisherFr)
        val wantEdition = normalizeEditionKey(draft.edition)
        return tomes.all { tome ->
            val publisher = normalizePublisherKey(tome.extras.readString(MangaExtras.KEY_PUBLISHER_FR))
            val edition = normalizeEditionKey(tome.extras.readString(MangaExtras.KEY_EDITION))
            (publisher.isEmpty() || publisher == wantPublisher) &&
                (edition.isEmpty() || edition == wantEdition)
        }
    }

    /**
     * Statut de parution **de l'édition française**, pas de l'œuvre : une série terminée au Japon
     * peut encore paraître en France. `ongoing` si la BnF voit l'édition en cours ; `finished`
     * seulement quand l'œuvre est terminée ET que l'édition a rattrapé son nombre de tomes ;
     * sinon inconnu — mieux vaut ne rien affirmer que fermer le rattrapage à tort.
     */
    private fun publicationStatusOf(
        candidate: SeriesCandidate,
        edition: SeriesEditionOption,
        mapping: EditionMapping?
    ): String? {
        if (mapping?.ongoing == true || edition.ongoing) return STATUS_ONGOING
        val total = candidate.volumeCount ?: return null
        val known = mapping?.tomeCount ?: edition.tomeCount
        return if (candidate.status == "completed" && known >= total) STATUS_FINISHED else null
    }

    /** Tomes à importer : ceux de l'édition (si mapping) + garantie de présence du tome scanné. */
    private fun buildTargets(
        match: ExternalMatch,
        scannedIsbn: String,
        mapping: EditionMapping?
    ): List<TomeTarget> {
        val scannedTarget = scannedTarget(match, scannedIsbn)
        if (mapping == null) {
            return listOf(scannedTarget)
        }
        val normalizedScanned = normalizeIsbn(scannedIsbn)
        val tomes = mapping.tomes.map {
            // Préférer la jaquette du mapping (cache serveur chaud) ; à défaut, pour le tome scanné
            // présent dans l'énumération, conserver la jaquette connue du `/search` (`match.coverUrl`).
            it.toTarget(
                coverUrl = it.coverUrl
                    ?: match.coverUrl.takeIf { _ -> normalizeIsbn(it.isbn) == normalizedScanned }
            )
        }
        // Le tome scanné peut ne pas figurer dans l'énumération BnF : on l'ajoute pour le garantir.
        val scannedPresent = tomes.any { normalizeIsbn(it.isbn) == normalizedScanned }
        return if (scannedPresent) tomes else tomes + scannedTarget
    }

    private fun scannedTarget(match: ExternalMatch, scannedIsbn: String) = TomeTarget(
        isbn = scannedIsbn,
        volumeNumber = match.volumeNumber,
        titleFr = match.title,
        sourceVolumeRange = match.sourceVolumeRange,
        coverUrl = match.coverUrl,
        description = match.description
    )

    private fun EditionTome.toTarget(coverUrl: String?) = TomeTarget(
        isbn = isbn,
        volumeNumber = editionVolume,
        titleFr = titleFr,
        sourceVolumeRange = sourceVolumeRange,
        coverUrl = coverUrl,
        // Résumé par tome (330$a BnF → repli Google Books, côté serveur) ; jamais celui de série.
        description = description
    )

    /**
     * Crée les tomes [targets] de la série. [normalizedScanned] désigne le tome possédé ; `null`
     * (import par titre) ⇒ tous les tomes sont en wishlist.
     */
    private suspend fun importTomes(
        seriesId: String,
        seriesTitle: String,
        context: TomeContext,
        normalizedScanned: String?,
        targets: List<TomeTarget>
    ): Outcome {
        val author = context.author ?: DEFAULT_AUTHOR
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

            // Jaquette de CHAQUE tome : d'abord celle déjà connue (tome scanné ou cache serveur
            // chaud via `edition-mapping`), sinon résolution explicite par ISBN (`/v1/search/cover`).
            // L'URL distante est conservée (re-cache ultérieur) et téléchargée en best-effort.
            val recordId = UUID.randomUUID().toString()
            val coverRemoteUrl = resolveTomeCover(target, seriesTitle, context.edition)
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
                extras = buildTomeExtras(author, context, target),
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
     * URL de jaquette à cacher pour un tome, selon un ordre de coût croissant :
     * 1. [TomeTarget.coverUrl] déjà connue — jaquette du tome scanné (`/search`) ou cache serveur
     *    chaud renvoyé par `edition-mapping`. Aucun appel réseau.
     * 2. Sinon, pour un tome porteur d'ISBN, résolution **explicite** via `/v1/search/cover` (réseau,
     *    best-effort), en armant le repli par titre (titre série + n° de tome + édition) pour les ISBN
     *    papier FR sans notice illustrée. Un échec renvoie `null` : l'ISBN reste connu pour un re-cache
     *    ultérieur, mais l'import n'échoue jamais (politique réseau §6 : appel déclenché par l'import).
     */
    private suspend fun resolveTomeCover(
        target: TomeTarget,
        seriesTitle: String,
        edition: String?
    ): String? {
        target.coverUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        // Le tome scanné sans jaquette connue n'a pas d'autre source que son ISBN : même résolution,
        // avec le hint titre/tome/édition qui débloque le repli `intitle:` serveur.
        val isbn = normalizeIsbn(target.isbn) ?: return null
        return externalSearch.resolveCover(
            isbn = isbn,
            seriesTitleFr = seriesTitle,
            volumeNumber = target.volumeNumber,
            edition = edition
        ).getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
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
        context: TomeContext,
        target: TomeTarget
    ): Map<String, String> = buildMap {
        put(MangaExtras.KEY_ORIGINAL_AUTHOR, author)
        context.edition?.let { put(MangaExtras.KEY_EDITION, it) }
        context.publisherFr?.let { put(MangaExtras.KEY_PUBLISHER_FR, it) }
        target.sourceVolumeRange?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { put(MangaExtras.KEY_SOURCE_VOLUME_RANGE, it) }
        // Résumé propre au tome (330$a BnF → repli Google Books) ; jamais le synopsis de série.
        target.description?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { put(MangaExtras.KEY_TOME_SYNOPSIS, it) }
    }

    /** Ce que chaque tome créé hérite de sa série : auteur et identité d'édition (déjà nettoyés). */
    private data class TomeContext(
        val author: String?,
        val publisherFr: String?,
        val edition: String?
    ) {
        companion object {
            fun of(match: ExternalMatch) = TomeContext(
                author = match.creators.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
                publisherFr = match.publisherFr?.trim()?.takeIf { it.isNotEmpty() },
                edition = match.edition?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
    }

    /** Série à retrouver ou créer (champs déjà nettoyés, `null` = inconnu). */
    private data class SeriesDraft(
        val title: String,
        val author: String?,
        val publisherFr: String?,
        val edition: String?,
        val mangaId: String?,
        val synopsis: String?,
        val coverUrl: String?,
        val totalVolumes: Int?,
        val publicationStatus: String? = null
    )

    private data class TomeTarget(
        val isbn: String?,
        val volumeNumber: Int?,
        val titleFr: String?,
        val sourceVolumeRange: String? = null,
        /** Jaquette déjà connue (tome scanné ou cache serveur chaud), sinon résolue par ISBN. */
        val coverUrl: String? = null,
        /** Résumé propre au tome (330$a BnF → repli Google Books) ; null si aucune source par tome. */
        val description: String? = null
    )

    private data class Outcome(val created: Int, val skipped: Int)

    private companion object {
        const val DEFAULT_AUTHOR = "Inconnu"

        // `Series.publicationStatus` est un texte libre affiché tel quel dans le formulaire : on
        // l'écrit comme un utilisateur le saisirait, dans le vocabulaire que la synchro sait coder
        // (`FacadeMapper.toMangaStatusCode`) et que [isPublicationFinished] reconnaît.
        const val STATUS_ONGOING = "En cours"
        const val STATUS_FINISHED = "Terminée"
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
    /**
     * Import complet demandé mais `edition-mapping` indisponible : repli sur le tome scanné seul
     * (scan), ou série créée sans tome (import par titre).
     */
    val degradedFullImport: Boolean = false
)
