package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.normalizeIsbn
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.repository.ExternalSearchRepository
import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

/**
 * Rafraîchit les métadonnées (jaquettes / descriptions) d'une sélection d'entrées et/ou de séries en
 * re-interrogeant les sources externes via le backend. Cf. `docs/interne/spec-edition-multiple.md` §2.5.
 *
 * **Action explicite** (déclenchée par l'utilisateur depuis le mode sélection) : conforme à la politique
 * réseau de l'app (aucun appel externe en arrière-plan). Best-effort **par cible** : un échec n'interrompt
 * pas le lot ; le bilan est agrégé dans [RefreshResult].
 *
 * Granularité de la source (décidée, §2.5.1) :
 * - **vinyle** (entrée) → [ExternalSearchRepository.searchByBarcode] par `barcode` (Discogs). V1 : seule
 *   la **jaquette** est traitée — `Record` n'a pas de champ description vinyle (§2.5.3).
 * - **tome manga** (entrée) → jaquette par `isbn` ([ExternalSearchRepository.resolveCover], repli
 *   `intitle:`), description via [ExternalSearchRepository.searchByBarcode] (`isbn`) rangée dans
 *   `extras[`[MangaExtras.KEY_TOME_SYNOPSIS]`]`.
 * - **série manga** (nœud) → **un seul** [ExternalSearchRepository.editionMapping] : jaquette du header
 *   (1er tome connu) puis **comblement des tomes existants** de la série (aucune création). `edition-mapping`
 *   ne porte pas de synopsis de série : le synopsis de la série n'est donc pas rafraîchi ici.
 *
 * Par défaut ([RefreshOptions.overwrite] = false) le refresh est **non destructif** : il ne remplit que
 * ce qui manque (jaquette absente / description vide) et n'écrase jamais une valeur présente.
 */
class RefreshMetadataUseCase @Inject constructor(
    private val recordRepository: RecordRepository,
    private val seriesRepository: SeriesRepository,
    private val externalSearch: ExternalSearchRepository,
    private val coverStorage: CoverStorage,
    private val clock: Clock
) {

    suspend operator fun invoke(
        recordIds: Collection<String> = emptyList(),
        seriesIds: Collection<String> = emptyList(),
        options: RefreshOptions = RefreshOptions()
    ): RefreshResult {
        val refreshed = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (id in recordIds.distinct()) {
            val record = recordRepository.observeById(id).first() ?: continue
            when (refreshRecord(record, options)) {
                Outcome.REFRESHED -> refreshed += id
                Outcome.UNCHANGED -> unchanged += id
                Outcome.FAILED -> failed += id
            }
        }

        for (id in seriesIds.distinct()) {
            val series = seriesRepository.observeById(id).first() ?: continue
            refreshSeries(series, options, refreshed, unchanged, failed)
        }

        return RefreshResult(refreshed = refreshed, unchanged = unchanged, failed = failed)
    }

    // ---- Entrées -------------------------------------------------------------------------------

    private suspend fun refreshRecord(record: Record, options: RefreshOptions): Outcome =
        when (record.collectionType) {
            CollectionType.VINYL -> refreshVinyl(record, options)
            CollectionType.MANGA -> refreshMangaTome(record, options)
        }

    private suspend fun refreshVinyl(record: Record, options: RefreshOptions): Outcome {
        if (!needsCover(record, options)) return Outcome.UNCHANGED
        val barcode = record.barcode?.trim()?.takeIf { it.isNotEmpty() } ?: return Outcome.UNCHANGED

        val matches = externalSearch.searchByBarcode(barcode, CollectionType.VINYL.typeCode)
            .getOrElse { return Outcome.FAILED }
        val coverUrl = matches.firstNotNullOfOrNull { it.coverUrl.nonBlank() }
            ?: return Outcome.UNCHANGED

        persistRecordCover(record, coverUrl)
        return Outcome.REFRESHED
    }

    private suspend fun refreshMangaTome(record: Record, options: RefreshOptions): Outcome {
        val wantCover = needsCover(record, options)
        val wantDescription =
            options.overwrite || record.extras[MangaExtras.KEY_TOME_SYNOPSIS].isNullOrBlank()
        if (!wantCover && !wantDescription) return Outcome.UNCHANGED

        val isbn = normalizeIsbn(record.isbn) ?: return Outcome.UNCHANGED

        var hadError = false
        var coverUrl: String? = null
        var description: String? = null

        // La description ne vient que du `/search` par ISBN, qui renvoie aussi une jaquette : on en
        // profite pour la capter en un seul appel quand la jaquette est également recherchée.
        if (wantDescription) {
            externalSearch.searchByBarcode(isbn, CollectionType.MANGA.typeCode).fold(
                onSuccess = { matches ->
                    val match = matches.firstOrNull()
                    description = match?.description.nonBlank()
                    if (wantCover) coverUrl = match?.coverUrl.nonBlank()
                },
                onFailure = { hadError = true }
            )
        }

        // Jaquette non trouvée par `/search` (ou description non demandée) : résolution ciblée par ISBN,
        // armée du repli `intitle:` (titre série + n° tome + édition) pour les ISBN papier FR sans notice.
        if (wantCover && coverUrl == null) {
            val seriesTitle = record.seriesId?.let { seriesRepository.observeById(it).first()?.title }
            val edition = record.extras[MangaExtras.KEY_EDITION].nonBlank()
            externalSearch.resolveCover(isbn, seriesTitle, record.volumeNumber, edition).fold(
                onSuccess = { coverUrl = it.nonBlank() },
                onFailure = { hadError = true }
            )
        }

        var updated = record
        var changed = false
        coverUrl?.let { url ->
            updated = updated.copy(coverRemoteUrl = url, coverPath = cacheCover(record.id, url))
            changed = true
        }
        description?.let { desc ->
            updated = updated.copy(extras = updated.extras + (MangaExtras.KEY_TOME_SYNOPSIS to desc))
            changed = true
        }

        return when {
            changed -> {
                recordRepository.upsert(updated.copy(updatedAt = clock.instant()))
                Outcome.REFRESHED
            }
            hadError -> Outcome.FAILED
            else -> Outcome.UNCHANGED
        }
    }

    private suspend fun persistRecordCover(record: Record, coverUrl: String) {
        recordRepository.upsert(
            record.copy(
                coverRemoteUrl = coverUrl,
                coverPath = cacheCover(record.id, coverUrl),
                updatedAt = clock.instant()
            )
        )
    }

    // ---- Séries --------------------------------------------------------------------------------

    /**
     * Rafraîchit une série via **un seul** `edition-mapping` : jaquette du header (best-effort) puis
     * comblement des tomes déjà présents à partir des données du mapping (jamais de résolution par tome —
     * la version ciblée par ISBN reste l'apanage du refresh d'un tome). Aucun tome n'est créé.
     */
    private suspend fun refreshSeries(
        series: Series,
        options: RefreshOptions,
        refreshed: MutableList<String>,
        unchanged: MutableList<String>,
        failed: MutableList<String>
    ) {
        // L'édition de la série (v8) cible la bonne énumération : sans elle, une colossale se
        // rafraîchissait depuis la standard. Séries d'avant la v8 : `null`, comportement inchangé.
        val mapping = externalSearch.editionMapping(
            titleFr = series.title,
            edition = series.edition,
            publisher = series.publisherFr,
            mangaId = series.mangaId
        ).getOrElse {
            failed += series.id
            return
        }

        if (refreshSeriesCover(series, mapping, options)) refreshed += series.id else unchanged += series.id

        val tomes = recordRepository.observeBySeriesId(series.id).first()
        for (tome in tomes) {
            val editionTome = matchTome(tome, mapping)
            if (applyMappingToTome(tome, editionTome, options)) refreshed += tome.id else unchanged += tome.id
        }
    }

    private suspend fun refreshSeriesCover(
        series: Series,
        mapping: EditionMapping,
        options: RefreshOptions
    ): Boolean {
        if (!(options.overwrite || series.coverPath == null)) return false
        val coverUrl = mapping.tomes.sortedBy { it.editionVolume }
            .firstNotNullOfOrNull { it.coverUrl.nonBlank() } ?: return false
        // `Series` n'a pas de `coverRemoteUrl` : sans téléchargement réussi, rien à persister.
        val path = cacheCover(series.id, coverUrl) ?: return false
        seriesRepository.upsert(series.copy(coverPath = path, updatedAt = clock.instant()))
        return true
    }

    private suspend fun applyMappingToTome(
        tome: Record,
        editionTome: EditionTome?,
        options: RefreshOptions
    ): Boolean {
        if (editionTome == null) return false

        var updated = tome
        var changed = false
        if (needsCover(tome, options)) {
            editionTome.coverUrl.nonBlank()?.let { url ->
                updated = updated.copy(coverRemoteUrl = url, coverPath = cacheCover(tome.id, url))
                changed = true
            }
        }
        val wantDescription =
            options.overwrite || tome.extras[MangaExtras.KEY_TOME_SYNOPSIS].isNullOrBlank()
        if (wantDescription) {
            editionTome.description.nonBlank()?.let { desc ->
                updated = updated.copy(extras = updated.extras + (MangaExtras.KEY_TOME_SYNOPSIS to desc))
                changed = true
            }
        }

        if (changed) recordRepository.upsert(updated.copy(updatedAt = clock.instant()))
        return changed
    }

    private fun matchTome(tome: Record, mapping: EditionMapping): EditionTome? {
        val isbn = normalizeIsbn(tome.isbn)
        if (isbn != null) {
            mapping.tomes.firstOrNull { normalizeIsbn(it.isbn) == isbn }?.let { return it }
        }
        return tome.volumeNumber?.let { vol -> mapping.tomes.firstOrNull { it.editionVolume == vol } }
    }

    // ---- Communs -------------------------------------------------------------------------------

    /** Jaquette à (re)chercher si on force le remplacement, ou si aucune n'est connue (locale ou distante). */
    private fun needsCover(record: Record, options: RefreshOptions): Boolean =
        options.overwrite || (record.coverPath == null && record.coverRemoteUrl == null)

    /**
     * Télécharge et met en cache local la jaquette référencée par [url]. Best-effort : renvoie le chemin
     * local, ou `null` si l'URL est vide ou si le téléchargement échoue (réseau) — le refresh ne doit
     * jamais échouer sur une jaquette (l'URL distante reste conservée pour un re-cache ultérieur).
     */
    private suspend fun cacheCover(id: String, url: String?): String? {
        val cleanUrl = url.nonBlank() ?: return null
        return runCatching { coverStorage.saveFromUrl(id, cleanUrl) }.getOrNull()
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private enum class Outcome { REFRESHED, UNCHANGED, FAILED }
}

/** Options du rafraîchissement (§2.5.2). */
data class RefreshOptions(
    /**
     * `false` (défaut) : **combler** — ne récupère que jaquette absente / description vide, n'écrase
     * jamais une valeur présente. `true` : **remplacer** — re-résout et écrase jaquette + description.
     */
    val overwrite: Boolean = false
)

/**
 * Bilan d'un refresh, par identifiant de cible (entrée **ou** série). Une série rafraîchie contribue son
 * propre id **et** ceux de ses tomes comblés.
 */
data class RefreshResult(
    val refreshed: List<String> = emptyList(),
    val unchanged: List<String> = emptyList(),
    val failed: List<String> = emptyList()
) {
    val refreshedCount: Int get() = refreshed.size
    val unchangedCount: Int get() = unchanged.size
    val failedCount: Int get() = failed.size
    val hasFailures: Boolean get() = failed.isNotEmpty()
}
