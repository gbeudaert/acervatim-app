package app.acervatim.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.PressingGroup
import app.acervatim.domain.usecase.ExternalSearchResult
import app.acervatim.domain.usecase.SearchExternalByTextUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import app.acervatim.ui.scan.ScanPrefillHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recherche d'un disque par titre/artiste dans les bases externes — le chemin d'ajout des disques
 * **sans code-barres**, que le scan ne peut pas atteindre.
 *
 * La saisie est *debouncée* : sans cela, chaque frappe partirait en requête, et le quota Discogs
 * (≈ 60 req/min, à peine amorti par le cache serveur sur du texte libre) serait consommé par les
 * préfixes d'un mot que l'utilisateur est encore en train d'écrire.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ExternalSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchExternal: SearchExternalByTextUseCase,
    private val prefillHandoff: ScanPrefillHandoff
) : ViewModel() {

    /** Type de collection porté par la route. Défaut : vinyle, seul type ouvert à ce parcours. */
    private val collectionType: CollectionType =
        savedStateHandle.get<String>(AcervatimNavArgs.COLLECTION_TYPE)
            ?.let { runCatching { CollectionType.valueOf(it) }.getOrNull() }
            ?: CollectionType.VINYL

    private val _state = MutableStateFlow(ExternalSearchUiState())
    val state: StateFlow<ExternalSearchUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<ExternalSearchEvent?>(null)
    val events: StateFlow<ExternalSearchEvent?> = _events.asStateFlow()

    /** Requêtes brutes, telles que tapées ; c'est ce flux qui absorbe la cadence de frappe. */
    private val typedQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            typedQuery
                .debounce(DEBOUNCE_MS)
                .map { it.trim() }
                // Après le debounce : deux saisies qui retombent sur le même texte (une lettre
                // ajoutée puis effacée) ne relancent pas la recherche.
                .distinctUntilChanged()
                .collectLatest { query -> runSearch(query) }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        typedQuery.value = value
    }

    /** Relance la requête courante sans attendre le debounce (bouton « Réessayer »). */
    fun onRetry() {
        viewModelScope.launch { runSearch(_state.value.query.trim()) }
    }

    /** Déplie/replie un album pour en montrer les pressages. */
    fun onToggleGroup(key: String) {
        _state.update { current ->
            val expanded = current.expandedGroups
            current.copy(
                expandedGroups = if (key in expanded) expanded - key else expanded + key
            )
        }
    }

    /**
     * Retient un pressage : le match est déposé au passe-plat sous une clé que la route d'ajout
     * portera, exactement comme le scan le fait avec le code-barres.
     */
    fun onSelectPressing(match: ExternalMatch) {
        val key = prefillKeyOf(match)
        prefillHandoff.put(key, match)
        _events.value = ExternalSearchEvent.PrefillNewRecord(key)
    }

    fun onEventConsumed() {
        _events.value = null
    }

    private suspend fun runSearch(query: String) {
        if (query.length < SearchExternalByTextUseCase.MIN_QUERY_LENGTH) {
            _state.update { it.copy(step = SearchStep.Idle, expandedGroups = emptySet()) }
            return
        }
        _state.update { it.copy(step = SearchStep.Searching) }
        val step = when (val result = searchExternal(query, collectionType.typeCode)) {
            ExternalSearchResult.QueryTooShort -> SearchStep.Idle
            ExternalSearchResult.NotConnected -> SearchStep.NotConnected
            ExternalSearchResult.NoMatch -> SearchStep.NoMatch
            is ExternalSearchResult.Matches -> SearchStep.Results(result.groups)
            is ExternalSearchResult.Failed -> SearchStep.Failed(result.failure, result.provider)
        }
        // Une nouvelle recherche repart replié : les clés de l'ancienne liste n'ont plus de sens.
        _state.update { it.copy(step = step, expandedGroups = emptySet()) }
    }

    private companion object {
        /** Assez long pour laisser finir un mot, assez court pour ne pas paraître figé. */
        const val DEBOUNCE_MS = 400L
    }
}

/**
 * Clé de passe-plat d'un pressage. L'identité `source:sourceId` plutôt que le code-barres : un
 * disque trouvé par titre n'en a pas forcément — c'est même la raison d'être de cet écran.
 */
internal fun prefillKeyOf(match: ExternalMatch): String = "${match.source}:${match.sourceId}"

@Immutable
data class ExternalSearchUiState(
    val query: String = "",
    val step: SearchStep = SearchStep.Idle,
    /** Clés des albums dépliés ([PressingGroup.key]) ; vidé à chaque nouvelle recherche. */
    val expandedGroups: Set<String> = emptySet()
)

/**
 * État de la zone de résultats. Les états d'échec sont séparés à dessein : « aucun résultat » et
 * « accès refusé » ne se disent pas de la même façon, et le second demande une action.
 */
sealed interface SearchStep {

    /** Rien à montrer : requête vide ou trop courte. */
    data object Idle : SearchStep

    data object Searching : SearchStep

    data class Results(val groups: List<PressingGroup>) : SearchStep

    /** Recherche effectuée, réellement aucun disque trouvé. */
    data object NoMatch : SearchStep

    /** Aucune session : la recherche externe passe par le serveur, donc exige d'être connecté. */
    data object NotConnected : SearchStep

    data class Failed(val failure: ExternalSearchFailure, val provider: String?) : SearchStep
}

sealed interface ExternalSearchEvent {
    /** Le pressage choisi est déposé sous [prefillKey] : ouvrir le formulaire d'ajout avec. */
    data class PrefillNewRecord(val prefillKey: String) : ExternalSearchEvent
}
