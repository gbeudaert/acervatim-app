package app.acervatim.domain

import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ImportJob
import app.acervatim.domain.model.ImportJobState
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.model.SeriesEditionOption
import app.acervatim.domain.model.SeriesUpdate
import app.acervatim.domain.repository.ExternalSearchRepository
import java.time.LocalDate

/**
 * Fake d'[ExternalSearchRepository] : renvoie un résultat programmé, ou une erreur, et enregistre la
 * dernière requête reçue pour les assertions.
 */
class FakeExternalSearchRepository : ExternalSearchRepository {

    var result: Result<List<ExternalMatch>> = Result.success(emptyList())
    var lastBarcode: String? = null
    var lastType: String? = null
    var callCount: Int = 0

    /** Recherche texte : programmable séparément du code-barres (les deux chemins sont distincts). */
    var textResult: Result<List<ExternalMatch>> = Result.success(emptyList())
    val textQueries: MutableList<String> = mutableListOf()
    var lastTextType: String? = null
    val textCallCount: Int get() = textQueries.size

    /** Programmable pour les tests d'import de série ; échec par défaut (aucun mapping). */
    var editionMappingResult: Result<EditionMapping> =
        Result.failure(IllegalStateException("no mapping configured"))
    var lastEditionTitle: String? = null
    var lastEditionEdition: String? = null
    var editionMappingCallCount: Int = 0

    /** Résolution `/v1/search/cover` : jaquette par ISBN (aucune par défaut) ; échec programmable. */
    var coverByIsbn: Map<String, String> = emptyMap()
    var resolveCoverFailure: Throwable? = null
    val resolveCoverCalls: MutableList<String> = mutableListOf()

    /** Hint (titre série, n° tome, édition) reçu au dernier `resolveCover`, pour vérifier le repli. */
    var lastResolveCoverHint: Triple<String?, Int?, String?>? = null

    override suspend fun searchByBarcode(barcode: String, type: String): Result<List<ExternalMatch>> {
        callCount++
        lastBarcode = barcode
        lastType = type
        return result
    }

    override suspend fun searchByText(query: String, type: String): Result<List<ExternalMatch>> {
        textQueries += query
        lastTextType = type
        return textResult
    }

    var lastEditionPublisher: String? = null
    var lastEditionMangaId: String? = null
    var lastEditionSince: LocalDate? = null

    override suspend fun editionMapping(
        titleFr: String,
        edition: String?,
        publisher: String?,
        mangaId: String?,
        since: LocalDate?
    ): Result<EditionMapping> {
        editionMappingCallCount++
        lastEditionTitle = titleFr
        lastEditionEdition = edition
        lastEditionPublisher = publisher
        lastEditionMangaId = mangaId
        lastEditionSince = since
        return editionMappingResult
    }

    /** Recherche de séries manga par titre : programmable, requêtes mémorisées. */
    var seriesSearchResult: Result<List<SeriesCandidate>> = Result.success(emptyList())
    val seriesQueries: MutableList<String> = mutableListOf()

    /** Réponse par requête, éventuellement lente (`delay`) : prime sur [seriesSearchResult]. */
    var seriesSearchHandler: (suspend (String) -> Result<List<SeriesCandidate>>)? = null

    override suspend fun searchMangaSeries(query: String): Result<List<SeriesCandidate>> {
        seriesQueries += query
        return seriesSearchHandler?.invoke(query) ?: seriesSearchResult
    }

    /** Éditions d'une série : programmable ; derniers paramètres mémorisés. */
    var editionsResult: Result<List<SeriesEditionOption>> = Result.success(emptyList())
    var lastEditionsMangaId: String? = null
    var lastEditionsTitle: String? = null

    override suspend fun listEditions(
        mangaId: String,
        title: String?
    ): Result<List<SeriesEditionOption>> {
        lastEditionsMangaId = mangaId
        lastEditionsTitle = title
        return editionsResult
    }

    /** Nouveautés serveur (premium) : aucune par défaut. */
    var seriesUpdatesResult: Result<List<SeriesUpdate>> = Result.success(emptyList())
    var seriesUpdatesCallCount: Int = 0

    override suspend fun seriesUpdates(): Result<List<SeriesUpdate>> {
        seriesUpdatesCallCount++
        return seriesUpdatesResult
    }

    override suspend fun resolveCover(
        isbn: String,
        seriesTitleFr: String?,
        volumeNumber: Int?,
        edition: String?
    ): Result<String?> {
        resolveCoverCalls += isbn
        lastResolveCoverHint = Triple(seriesTitleFr, volumeNumber, edition)
        resolveCoverFailure?.let { return Result.failure(it) }
        return Result.success(coverByIsbn[isbn])
    }

    /** POST du job d'import. `null` → défaut : un job déjà `DONE` (aucun poll requis). */
    var startImportJobResult: Result<ImportJob>? = null
    var startImportJobCallCount: Int = 0
    var lastImportJobTitle: String? = null
    var lastImportJobEdition: String? = null

    /**
     * Statuts renvoyés par les `importJobStatus` successifs (le dernier se répète). Vide → défaut : un
     * statut `DONE`. Un élément `success(null)` simule un `404` ; `failure` une erreur réseau.
     */
    var importJobStatusSequence: List<Result<ImportJob?>> = emptyList()
    var importJobStatusCallCount: Int = 0

    var lastImportJobPublisher: String? = null
    var lastImportJobMangaId: String? = null

    override suspend fun startImportJob(
        titleFr: String,
        edition: String?,
        publisher: String?,
        mangaId: String?
    ): Result<ImportJob> {
        startImportJobCallCount++
        lastImportJobTitle = titleFr
        lastImportJobEdition = edition
        lastImportJobPublisher = publisher
        lastImportJobMangaId = mangaId
        return startImportJobResult
            ?: Result.success(ImportJob(jobId = "job-$titleFr", state = ImportJobState.DONE))
    }

    override suspend fun importJobStatus(jobId: String): Result<ImportJob?> {
        val seq = importJobStatusSequence
        val result = when {
            seq.isEmpty() -> Result.success(ImportJob(jobId = jobId, state = ImportJobState.DONE))
            else -> seq[importJobStatusCallCount.coerceAtMost(seq.lastIndex)]
        }
        importJobStatusCallCount++
        return result
    }
}
