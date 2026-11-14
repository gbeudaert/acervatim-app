package app.acervatim.domain.usecase

import app.acervatim.domain.model.ImportJobProgress
import app.acervatim.domain.model.ImportJobState
import app.acervatim.domain.repository.ExternalSearchRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Réchauffe le cache serveur d'une édition avant l'import local : lance le **job backend** d'import
 * (`POST /v1/search/import-jobs`, énumération BnF + jaquettes) puis poll son avancement jusqu'à
 * `DONE`/`FAILED` ou un garde-fou de temps.
 *
 * Partagé par les deux portes d'entrée de l'import complet — le scan d'un tome
 * ([app.acervatim.ui.series.SeriesImportViewModel]) et la recherche par titre
 * ([app.acervatim.ui.series.SeriesSearchViewModel]) : même job, mêmes paramètres, donc même entrée
 * de cache serveur que l'`edition-mapping` relu ensuite par [ImportScannedSeriesUseCase].
 *
 * Best-effort : ne renvoie rien. Toute issue (POST refusé, `404`, erreur réseau, timeout) laisse
 * simplement l'import local re-taper `edition-mapping` — le job est durable et idempotent (id
 * déterministe), il n'y a rien à rattraper côté app.
 */
class WarmEditionImportUseCase @Inject constructor(
    private val externalSearch: ExternalSearchRepository
) {

    /**
     * [onPolling] est appelé dès que le serveur a accepté le job, puis à chaque statut relu : c'est le
     * signal qui fait passer l'écran en « avancement » (l'écran n'a pas à connaître le polling).
     */
    suspend operator fun invoke(
        titleFr: String,
        edition: String?,
        publisher: String?,
        mangaId: String?,
        onPolling: (ImportJobProgress?) -> Unit
    ) {
        val job = externalSearch.startImportJob(
            titleFr = titleFr,
            edition = edition,
            publisher = publisher,
            mangaId = mangaId
        ).getOrNull() ?: return
        var state = job.state
        onPolling(job.progress)

        var elapsed = 0L
        var interval = POLL_INITIAL_MS
        while (state != ImportJobState.DONE && state != ImportJobState.FAILED && elapsed < POLL_MAX_WAIT_MS) {
            delay(interval)
            elapsed += interval
            interval = (interval * 2).coerceAtMost(POLL_MAX_INTERVAL_MS)
            // `null` = job inconnu (404) OU erreur réseau : on cesse de poll et on tente l'import local.
            val status = externalSearch.importJobStatus(job.jobId).getOrNull() ?: break
            state = status.state
            onPolling(status.progress)
        }
    }

    private companion object {
        /** Intervalle de poll initial (ms), doublé à chaque tour jusqu'à [POLL_MAX_INTERVAL_MS]. */
        const val POLL_INITIAL_MS = 500L
        const val POLL_MAX_INTERVAL_MS = 2_000L

        /** Garde-fou : au-delà, on bascule sur l'import local sans attendre davantage le job. */
        const val POLL_MAX_WAIT_MS = 120_000L
    }
}
