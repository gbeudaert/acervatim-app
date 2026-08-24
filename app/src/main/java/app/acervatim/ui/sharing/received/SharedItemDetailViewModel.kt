package app.acervatim.ui.sharing.received

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.sharing.SharedItemDetail
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharedCollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SharedItemState {
    data object Loading : SharedItemState
    data object Failed : SharedItemState

    /** Partage révoqué / membre éjecté (`404`) : l'accès est coupé, rien à réessayer. */
    data object Gone : SharedItemState

    /** Propriétaire non premium (`402`) : suspension, pas suppression. */
    data object Suspended : SharedItemState

    data class Ready(val item: SharedItemDetail) : SharedItemState
}

/**
 * Détail d'un élément partagé, **en lecture seule et en ligne**.
 *
 * Rien n'est écrit : ni Room, ni export. Et rien n'est éditable — le backend refuserait au guard
 * (`403`), mais l'écran ne construit simplement aucune action.
 */
@HiltViewModel
class SharedItemDetailViewModel @Inject constructor(
    private val repository: SharedCollectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>(ITEM_ID_ARG).orEmpty()

    private val _uiState = MutableStateFlow<SharedItemState>(SharedItemState.Loading)
    val uiState: StateFlow<SharedItemState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        _uiState.value = SharedItemState.Loading
        viewModelScope.launch {
            repository.item(itemId)
                .onSuccess { _uiState.value = SharedItemState.Ready(it) }
                .onFailure { error ->
                    _uiState.value = when (
                        (error as? SharingException)?.failure ?: SharingFailure.UNKNOWN
                    ) {
                        SharingFailure.GONE -> SharedItemState.Gone
                        SharingFailure.PREMIUM_REQUIRED -> SharedItemState.Suspended
                        else -> SharedItemState.Failed
                    }
                }
        }
    }

    companion object {
        const val ITEM_ID_ARG: String = "itemId"
    }
}
