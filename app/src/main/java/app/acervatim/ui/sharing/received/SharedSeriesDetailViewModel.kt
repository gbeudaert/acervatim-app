package app.acervatim.ui.sharing.received

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharedCollectionRepository
import app.acervatim.domain.repository.SharedSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SharedSeriesState {
    data object Loading : SharedSeriesState

    /** Réseau ou serveur : réessayable. */
    data object Failed : SharedSeriesState

    /**
     * Partage révoqué, membre éjecté, ou série retirée de la collection : l'accès à cette série
     * n'existe plus. État terminal — rien à réessayer, et rien à purger (aucune donnée locale).
     */
    data object Gone : SharedSeriesState

    /** Le **propriétaire** a perdu son premium : suspension, pas suppression. */
    data object Suspended : SharedSeriesState

    data class Ready(val series: SharedSeries) : SharedSeriesState
}

/**
 * Détail d'une série reçue en partage, **en lecture seule et en ligne**.
 *
 * Le rendu est celui des séries locales ([app.acervatim.ui.series.SeriesDetailBody]) : en-tête
 * dépliable, synopsis, grille de tomes. Ce qui change est la source — un nœud serveur, pas une ligne
 * Room — et l'absence totale d'actions.
 *
 * Chargement **ciblé** : le nœud et ses seuls tomes, pas toute la collection. Rouvrir une série ne
 * doit pas coûter le rechargement de ce qu'on vient de quitter.
 */
@HiltViewModel
class SharedSeriesDetailViewModel @Inject constructor(
    private val repository: SharedCollectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val collectionId: String = savedStateHandle.get<String>(COLLECTION_ID_ARG).orEmpty()
    private val seriesId: String = savedStateHandle.get<String>(SERIES_ID_ARG).orEmpty()

    private val _uiState = MutableStateFlow<SharedSeriesState>(SharedSeriesState.Loading)
    val uiState: StateFlow<SharedSeriesState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        _uiState.value = SharedSeriesState.Loading
        viewModelScope.launch {
            // Le type n'est pas transporté dans la route : une série n'existe que dans un type
            // hiérarchique, et le seul qu'il y ait aujourd'hui est le manga.
            repository.series(collectionId, seriesId, CollectionType.MANGA)
                .onSuccess { shared ->
                    _uiState.value = shared
                        ?.let { SharedSeriesState.Ready(it) }
                        ?: SharedSeriesState.Gone
                }
                .onFailure { error ->
                    _uiState.value = when (
                        (error as? SharingException)?.failure ?: SharingFailure.UNKNOWN
                    ) {
                        SharingFailure.GONE -> SharedSeriesState.Gone
                        SharingFailure.PREMIUM_REQUIRED -> SharedSeriesState.Suspended
                        else -> SharedSeriesState.Failed
                    }
                }
        }
    }

    companion object {
        const val COLLECTION_ID_ARG: String = "collectionId"
        const val SERIES_ID_ARG: String = "seriesId"
    }
}
