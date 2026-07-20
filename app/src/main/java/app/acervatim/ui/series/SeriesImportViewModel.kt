package app.acervatim.ui.series

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ImportJobProgress
import app.acervatim.domain.model.ImportJobState
import app.acervatim.domain.repository.ExternalSearchRepository
import app.acervatim.domain.usecase.ImportResult
import app.acervatim.domain.usecase.ImportScannedSeriesUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import app.acervatim.ui.scan.ScanPrefillHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pilote l'écran d'import de série lancé après un scan ISBN depuis la **vue séries**. Récupère le
 * résultat enrichi déposé par le scan ([ScanPrefillHandoff]) pour proposer l'import « tous les tomes
 * connus » (édition complète via le backend) ou « tome scanné seul ».
 *
 * **Import complet asynchrone (P1)** : au lieu d'appeler `edition-mapping` en synchrone (fan-out lent
 * BnF + ~30 jaquettes → timeout scan, incident 0.5.3), on lance un **job backend** (fire-and-poll),
 * on affiche son avancement, puis — quand le worker a réchauffé le cache serveur — on déroule l'import
 * local ([ImportScannedSeriesUseCase.importFromList], dont le `edition-mapping` interne est alors servi
 * du cache, < 1 s). Le job étant durable + idempotent (`jobId` déterministe), toute erreur (POST KO,
 * Redis down, job `failed`, `404`, timeout de garde) tombe proprement sur l'import local, qui dégrade
 * en « tome scanné seul ». Machine à états : `OPTIONS → LAUNCHING → POLLING → IMPORTING → DONE`.
 *
 * Si aucun résultat n'a été déposé (hors-ligne, code inconnu), l'écran propose un repli vers la
 * saisie manuelle. Voir `docs/interne/spec-scan-import-serie.md`.
 */
@HiltViewModel
class SeriesImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    prefillHandoff: ScanPrefillHandoff,
    private val externalSearch: ExternalSearchRepository,
    private val importSeries: ImportScannedSeriesUseCase
) : ViewModel() {

    private val barcode: String = savedStateHandle.get<String>(AcervatimNavArgs.BARCODE).orEmpty()
    private val match: ExternalMatch? =
        barcode.takeIf { it.isNotBlank() }?.let { prefillHandoff.take(it) }

    private val _state = MutableStateFlow(
        SeriesImportUiState(
            barcode = barcode,
            hasMatch = match != null,
            seriesTitle = match?.let { it.seriesTitleFr ?: it.title },
            scannedVolume = match?.volumeNumber
        )
    )
    val state: StateFlow<SeriesImportUiState> = _state.asStateFlow()

    fun onToggleImportAll(value: Boolean) = _state.update { it.copy(importAll = value) }

    fun onImport() {
        val current = match ?: return
        if (_state.value.importing || _state.value.result != null) return
        // Garde de ré-entrance synchrone (avant le launch), comme l'ancien `importing = true`.
        _state.update { it.copy(phase = ScanImportPhase.LAUNCHING) }
        viewModelScope.launch {
            // Import complet : on réchauffe d'abord le cache serveur via le job async (best-effort).
            if (_state.value.importAll && !current.seriesTitleFr.isNullOrBlank()) {
                warmCacheViaJob(current.seriesTitleFr, current.edition)
            }
            runLocalImport(current)
        }
    }

    /**
     * Lance le job d'import async et poll son avancement jusqu'à `DONE`/`FAILED` (ou garde-fou de
     * temps). Best-effort : ne renvoie rien ; toute issue (échec POST, `404`, erreur réseau, timeout)
     * laisse simplement l'import local re-taper `edition-mapping` (cache probablement chaud à ce stade,
     * sinon dégradation « tome seul »).
     */
    private suspend fun warmCacheViaJob(titleFr: String, edition: String?) {
        val job = externalSearch.startImportJob(titleFr, edition).getOrNull() ?: return
        var state = job.state
        _state.update { it.copy(phase = ScanImportPhase.POLLING, progress = job.progress) }

        var elapsed = 0L
        var interval = POLL_INITIAL_MS
        while (state != ImportJobState.DONE && state != ImportJobState.FAILED && elapsed < POLL_MAX_WAIT_MS) {
            delay(interval)
            elapsed += interval
            interval = (interval * 2).coerceAtMost(POLL_MAX_INTERVAL_MS)
            // `null` = job inconnu (404) OU erreur réseau : on cesse de poll et on tente l'import local.
            val status = externalSearch.importJobStatus(job.jobId).getOrNull() ?: break
            state = status.state
            _state.update { it.copy(progress = status.progress) }
        }
    }

    private suspend fun runLocalImport(current: ExternalMatch) {
        _state.update { it.copy(phase = ScanImportPhase.IMPORTING, progress = null) }
        val result = importSeries.importFromList(
            scannedIsbn = barcode,
            match = current,
            importAllKnownTomes = _state.value.importAll
        )
        _state.update { it.copy(phase = ScanImportPhase.DONE, result = result) }
    }

    private companion object {
        /** Intervalle de poll initial (ms), doublé à chaque tour jusqu'à [POLL_MAX_INTERVAL_MS]. */
        const val POLL_INITIAL_MS = 500L
        const val POLL_MAX_INTERVAL_MS = 2_000L

        /** Garde-fou : au-delà, on bascule sur l'import local sans attendre davantage le job. */
        const val POLL_MAX_WAIT_MS = 120_000L
    }
}

/** Étape courante de l'import de série (pilote l'affichage : options / avancement / résultat). */
enum class ScanImportPhase {
    /** Choix « tous les tomes / tome seul » + bouton Importer. */
    OPTIONS,

    /** POST du job d'import lancé, en attente du `jobId`. */
    LAUNCHING,

    /** Job en cours côté backend, poll de l'avancement ([SeriesImportUiState.progress]). */
    POLLING,

    /** Cache serveur chaud : import local en base (création série + tomes). */
    IMPORTING,

    /** Terminé — [SeriesImportUiState.result] disponible. */
    DONE
}

@Immutable
data class SeriesImportUiState(
    val barcode: String = "",
    val hasMatch: Boolean = false,
    val seriesTitle: String? = null,
    val scannedVolume: Int? = null,
    val importAll: Boolean = true,
    val phase: ScanImportPhase = ScanImportPhase.OPTIONS,
    /** Avancement du job async (phase + done/total), quand disponible. */
    val progress: ImportJobProgress? = null,
    val result: ImportResult? = null
) {
    /** Vrai dès qu'un import est en vol (lancement / poll / écriture locale). */
    val importing: Boolean
        get() = phase == ScanImportPhase.LAUNCHING ||
            phase == ScanImportPhase.POLLING ||
            phase == ScanImportPhase.IMPORTING
}
