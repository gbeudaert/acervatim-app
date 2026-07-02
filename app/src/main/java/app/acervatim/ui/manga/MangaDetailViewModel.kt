package app.acervatim.ui.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.usecase.DeleteRecordUseCase
import app.acervatim.domain.usecase.ObserveRecordByIdUseCase
import app.acervatim.domain.usecase.ObserveSeriesByIdUseCase
import app.acervatim.domain.usecase.ToggleWishlistUseCase
import app.acervatim.domain.usecase.UpdateRatingUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Détail d'un tome manga. Observe le [Record] du tome ET sa [Series] (pour le synopsis de série,
 * les mangas n'ayant pas de résumé par tome côté sources). Cf. écran vinyle [RecordDetailViewModel]
 * pour le pattern ; ici le rendu est spécifique manga (n° de tome, édition, mapping inter-édition).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MangaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecord: ObserveRecordByIdUseCase,
    observeSeries: ObserveSeriesByIdUseCase,
    private val toggleWishlist: ToggleWishlistUseCase,
    private val updateRating: UpdateRatingUseCase,
    private val deleteRecord: DeleteRecordUseCase
) : ViewModel() {

    private val recordId: String =
        checkNotNull(savedStateHandle.get<String>(AcervatimNavArgs.RECORD_ID)) {
            "Missing nav argument: ${AcervatimNavArgs.RECORD_ID}"
        }

    val uiState: StateFlow<MangaDetailUiState> = observeRecord(recordId)
        .flatMapLatest { record ->
            when {
                record == null -> flowOf(MangaDetailUiState.NotFound)
                record.seriesId == null ->
                    flowOf(MangaDetailUiState.Loaded(record, series = null))
                else -> observeSeries(record.seriesId).map { series ->
                    MangaDetailUiState.Loaded(record, series)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MangaDetailUiState.Loading
        )

    private val _events = MutableStateFlow<MangaDetailEvent?>(null)
    val events: StateFlow<MangaDetailEvent?> = _events.asStateFlow()

    fun onWishlistToggle(wishlist: Boolean) = viewModelScope.launch {
        toggleWishlist(recordId, wishlist)
    }

    fun onRatingChange(rating: Int) = viewModelScope.launch {
        updateRating(recordId, rating)
    }

    fun onDelete() = viewModelScope.launch {
        deleteRecord(recordId)
        _events.value = MangaDetailEvent.Deleted
    }

    fun onEventConsumed() {
        _events.value = null
    }
}

sealed interface MangaDetailEvent {
    data object Deleted : MangaDetailEvent
}

@Immutable
sealed interface MangaDetailUiState {
    data object Loading : MangaDetailUiState
    data object NotFound : MangaDetailUiState

    @Immutable
    data class Loaded(val record: Record, val series: Series?) : MangaDetailUiState
}
