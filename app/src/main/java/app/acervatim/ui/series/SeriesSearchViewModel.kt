package app.acervatim.ui.series

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.ImportJobProgress
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.model.SeriesEditionOption
import app.acervatim.domain.repository.ExternalSearchRepository
import app.acervatim.domain.usecase.ImportResult
import app.acervatim.domain.usecase.ImportScannedSeriesUseCase
import app.acervatim.domain.usecase.SearchMangaSeriesUseCase
import app.acervatim.domain.usecase.SeriesSearchResult
import app.acervatim.domain.usecase.WarmEditionImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ajoute une série manga **par titre, sans en posséder aucun tome** — la porte d'entrée de la
 * wishlist (`spec-macro-import-serie-manga.md`). Trois étapes, chacune un choix de l'utilisateur :
 *
 * 1. **la série** parmi les candidats MangaDex (saisie debouncée, comme la recherche de disques) ;
 * 2. **l'édition** française parmi celles que la BnF catalogue (éditeur + mention : la standard Pika
 *    et la 2-en-1 France Loisirs sont deux éditions, pas une) ;
 * 3. **l'import** : job serveur qui réchauffe l'énumération et les jaquettes, puis création locale de
 *    tous les tomes en `WISHLIST` ([ImportScannedSeriesUseCase.importFromSearch]).
 *
 * Le choix revient à l'utilisateur à chaque étape parce que le titre seul ne départage pas (le
 * crossover homonyme de « Frieren ») et qu'une édition ne se devine pas. Le `mangaId` du candidat
 * retenu suit jusqu'à l'import : le serveur joint les jaquettes directement, sans rapprochement.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SeriesSearchViewModel @Inject constructor(
    private val searchSeries: SearchMangaSeriesUseCase,
    private val externalSearch: ExternalSearchRepository,
    private val warmEditionImport: WarmEditionImportUseCase,
    private val importSeries: ImportScannedSeriesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesSearchUiState())
    val state: StateFlow<SeriesSearchUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<SeriesSearchEvent?>(null)
    val events: StateFlow<SeriesSearchEvent?> = _events.asStateFlow()

    /** Requêtes brutes, telles que tapées ; c'est ce flux qui absorbe la cadence de frappe. */
    private val typedQuery = MutableStateFlow("")

    /**
     * Relances explicites (« Réessayer »). Elles rejoignent le **même** `collectLatest` que la saisie :
     * une relance lente ne peut plus revenir après une recherche plus récente et l'écraser — la
     * nouvelle requête l'annule, comme une frappe annule la précédente.
     */
    private val retryRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Chargement des éditions en vol : annulé si l'utilisateur revient aux candidats entre-temps. */
    private var editionsJob: Job? = null

    init {
        viewModelScope.launch {
            val typed = typedQuery
                .debounce(DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()
            merge(typed, retryRequests)
                .collectLatest { query -> runSearch(query) }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        typedQuery.value = value
    }

    /** Relance la recherche courante sans attendre le debounce (bouton « Réessayer »). */
    fun onRetry() {
        retryRequests.tryEmit(_state.value.query.trim())
    }

    /** Retient une série : passe à l'étape des éditions et les charge. */
    fun onSelectCandidate(candidate: SeriesCandidate) {
        if (_state.value.stage != SeriesSearchStage.SEARCH) return
        _state.update { it.copy(selected = candidate, editions = EditionsStep.Loading) }
        loadEditions(candidate)
    }

    fun onRetryEditions() {
        val candidate = _state.value.selected ?: return
        if (_state.value.stage != SeriesSearchStage.EDITIONS) return
        _state.update { it.copy(editions = EditionsStep.Loading) }
        loadEditions(candidate)
    }

    /**
     * Retour arrière **interne** à l'écran. Renvoie `true` si l'écran l'a consommé : des éditions on
     * revient aux candidats (la liste est conservée, pas besoin de retaper) ; pendant l'import on
     * bloque — quitter tuerait la coroutine au milieu des écritures et laisserait une série à moitié
     * créée. `false` : l'appelant quitte l'écran.
     */
    fun onBack(): Boolean {
        val current = _state.value
        return when {
            current.importing -> true
            // Import en échec : retour au choix de l'édition, rien n'est en vol.
            current.importFailed -> {
                _state.update {
                    it.copy(chosenEdition = null, importPhase = null, progress = null, importFailed = false)
                }
                true
            }
            current.stage == SeriesSearchStage.EDITIONS -> {
                editionsJob?.cancel()
                _state.update { it.copy(selected = null, editions = EditionsStep.Loading) }
                true
            }
            else -> false
        }
    }

    /** Retient une édition et lance l'import (job serveur puis création locale des tomes). */
    fun onSelectEdition(option: SeriesEditionOption) {
        val candidate = _state.value.selected ?: return
        // Garde de ré-entrance synchrone : un double tap ne lance pas deux imports.
        if (_state.value.stage != SeriesSearchStage.EDITIONS) return
        startImport(candidate, option)
    }

    /** Relance un import qui a échoué, sur la même édition. */
    fun onRetryImport() {
        val current = _state.value
        val candidate = current.selected ?: return
        val option = current.chosenEdition ?: return
        if (!current.importFailed) return
        startImport(candidate, option)
    }

    private fun startImport(candidate: SeriesCandidate, option: SeriesEditionOption) {
        _state.update {
            it.copy(
                chosenEdition = option,
                importPhase = ScanImportPhase.LAUNCHING,
                progress = null,
                importFailed = false
            )
        }
        viewModelScope.launch {
            try {
                // Mêmes paramètres que l'`edition-mapping` relu par l'import local : le job réchauffe
                // exactement l'entrée de cache que l'import va lire, jaquettes comprises.
                warmEditionImport(
                    titleFr = option.seriesTitleFr,
                    edition = option.edition,
                    publisher = option.publisher,
                    mangaId = candidate.mangaId
                ) { progress ->
                    _state.update { it.copy(importPhase = ScanImportPhase.POLLING, progress = progress) }
                }
                _state.update { it.copy(importPhase = ScanImportPhase.IMPORTING, progress = null) }
                val result = importSeries.importFromSearch(candidate, option)
                _state.update { it.copy(importPhase = ScanImportPhase.DONE, result = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Écriture locale ou réseau en échec : sans ce filet, `importing` restait vrai à
                // jamais — écran figé, retour bloqué. On relâche l'import et on le dit ; l'utilisateur
                // peut réessayer ou revenir aux éditions. Les tomes déjà écrits restent (dédup ISBN
                // au prochain essai).
                _state.update { it.copy(importPhase = null, progress = null, importFailed = true) }
            }
        }
    }

    /**
     * Aucune édition française exploitable : on bascule vers la création manuelle, pré-remplie avec
     * ce que la recherche a déjà appris (titre FR si connu, premier auteur) plutôt qu'une liste vide.
     */
    fun onCreateManually() {
        val candidate = _state.value.selected ?: return
        _events.value = SeriesSearchEvent.CreateManually(
            title = candidate.titleFr ?: candidate.title,
            author = candidate.authors.firstOrNull()
        )
    }

    fun onEventConsumed() {
        _events.value = null
    }

    private fun loadEditions(candidate: SeriesCandidate) {
        editionsJob?.cancel()
        editionsJob = viewModelScope.launch {
            // Un candidat du repli MAL n'a pas d'id MangaDex : le serveur ne peut pas lister ses
            // éditions. Ce n'est pas une panne, juste l'absence de clé — même issue qu'une édition
            // introuvable : la création manuelle.
            val mangaId = candidate.mangaId
            val step = if (mangaId == null) {
                EditionsStep.Empty
            } else {
                // La saisie sert d'indice de titre au serveur, après le titre FR MangaDex : quand
                // l'œuvre n'a pas de titre français référencé (« Frieren » chez Ki-oon), c'est souvent
                // ce que l'utilisateur a tapé que la BnF connaît.
                val hint = _state.value.query.trim().takeIf { it.isNotEmpty() }
                externalSearch.listEditions(mangaId, hint).fold(
                    onSuccess = { options ->
                        if (options.isEmpty()) EditionsStep.Empty else EditionsStep.Loaded(options)
                    },
                    onFailure = { cause ->
                        EditionsStep.Failed(
                            (cause as? ExternalSearchException)?.failure ?: ExternalSearchFailure.UNKNOWN
                        )
                    }
                )
            }
            _state.update { if (it.selected == candidate) it.copy(editions = step) else it }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.length < SearchMangaSeriesUseCase.MIN_QUERY_LENGTH) {
            _state.update { it.copy(step = SeriesSearchStep.Idle) }
            return
        }
        _state.update { it.copy(step = SeriesSearchStep.Searching) }
        val step = when (val result = searchSeries(query)) {
            SeriesSearchResult.QueryTooShort -> SeriesSearchStep.Idle
            SeriesSearchResult.NotConnected -> SeriesSearchStep.NotConnected
            SeriesSearchResult.NoMatch -> SeriesSearchStep.NoMatch
            is SeriesSearchResult.Matches -> SeriesSearchStep.Results(result.candidates)
            is SeriesSearchResult.Failed -> SeriesSearchStep.Failed(result.failure, result.provider)
        }
        _state.update { it.copy(step = step) }
    }

    private companion object {
        /** Même cadence que la recherche de disques : assez pour finir un mot, pas assez pour figer. */
        const val DEBOUNCE_MS = 400L
    }
}

/** Étape visible de l'écran, déduite de l'état (pas stockée : elle ne peut pas diverger). */
enum class SeriesSearchStage { SEARCH, EDITIONS, IMPORT }

@Immutable
data class SeriesSearchUiState(
    val query: String = "",
    val step: SeriesSearchStep = SeriesSearchStep.Idle,
    /** Série retenue ; non nulle = on choisit son édition. */
    val selected: SeriesCandidate? = null,
    val editions: EditionsStep = EditionsStep.Loading,
    /** Édition retenue ; non nulle = l'import est lancé (ou terminé). */
    val chosenEdition: SeriesEditionOption? = null,
    val importPhase: ScanImportPhase? = null,
    /** Avancement du job serveur, quand il en publie un. */
    val progress: ImportJobProgress? = null,
    val result: ImportResult? = null,
    /** L'import a échoué (exception) : rien n'est en vol, l'écran propose de réessayer ou revenir. */
    val importFailed: Boolean = false
) {
    val stage: SeriesSearchStage
        get() = when {
            chosenEdition != null -> SeriesSearchStage.IMPORT
            selected != null -> SeriesSearchStage.EDITIONS
            else -> SeriesSearchStage.SEARCH
        }

    /** Import en vol (lancement / poll / écriture locale) : ni retour ni second import. */
    val importing: Boolean
        get() = importPhase != null && importPhase != ScanImportPhase.DONE
}

/**
 * Zone de résultats de l'étape 1. Échecs séparés à dessein, comme pour les disques : « aucune série »
 * et « accès refusé » ne se disent pas de la même façon, et le second demande une action.
 */
sealed interface SeriesSearchStep {
    data object Idle : SeriesSearchStep
    data object Searching : SeriesSearchStep
    data class Results(val candidates: List<SeriesCandidate>) : SeriesSearchStep
    data object NoMatch : SeriesSearchStep
    data object NotConnected : SeriesSearchStep
    data class Failed(val failure: ExternalSearchFailure, val provider: String?) : SeriesSearchStep
}

/** Étape 2 : les éditions françaises de la série retenue. */
sealed interface EditionsStep {
    data object Loading : EditionsStep
    data class Loaded(val options: List<SeriesEditionOption>) : EditionsStep

    /** Aucune édition catalogée (ou candidat sans id MangaDex) : on propose la création manuelle. */
    data object Empty : EditionsStep
    data class Failed(val failure: ExternalSearchFailure) : EditionsStep
}

sealed interface SeriesSearchEvent {
    /** Ouvrir la création manuelle de série, pré-remplie. */
    data class CreateManually(val title: String, val author: String?) : SeriesSearchEvent
}
