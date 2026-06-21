package app.acervatim.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.usecase.DeleteRecordUseCase
import app.acervatim.domain.usecase.MarkAsPlayedUseCase
import app.acervatim.domain.usecase.ObserveRecordByIdUseCase
import app.acervatim.domain.usecase.ResetLastPlayedUseCase
import app.acervatim.domain.usecase.ToggleWishlistUseCase
import app.acervatim.domain.usecase.UpdateRatingUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecord: ObserveRecordByIdUseCase,
    private val markAsPlayed: MarkAsPlayedUseCase,
    private val resetLastPlayed: ResetLastPlayedUseCase,
    private val toggleWishlist: ToggleWishlistUseCase,
    private val updateRating: UpdateRatingUseCase,
    private val deleteRecord: DeleteRecordUseCase
) : ViewModel() {

    private val recordId: String =
        checkNotNull(savedStateHandle.get<String>(AcervatimNavArgs.RECORD_ID)) {
            "Missing nav argument: ${AcervatimNavArgs.RECORD_ID}"
        }

    val uiState: StateFlow<RecordDetailUiState> = observeRecord(recordId)
        .map { record ->
            if (record == null) RecordDetailUiState.NotFound
            else RecordDetailUiState.Loaded(record)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordDetailUiState.Loading
        )

    private val _events = MutableStateFlow<RecordDetailEvent?>(null)
    val events: StateFlow<RecordDetailEvent?> = _events.asStateFlow()

    fun onMarkAsPlayed() = viewModelScope.launch {
        markAsPlayed(recordId)
    }

    fun onResetLastPlayed() = viewModelScope.launch {
        resetLastPlayed(recordId)
    }

    fun onWishlistToggle(wishlist: Boolean) = viewModelScope.launch {
        toggleWishlist(recordId, wishlist)
    }

    fun onRatingChange(rating: Int) = viewModelScope.launch {
        updateRating(recordId, rating)
    }

    fun onDelete() = viewModelScope.launch {
        deleteRecord(recordId)
        _events.value = RecordDetailEvent.Deleted
    }

    fun onEventConsumed() {
        _events.value = null
    }
}

@Immutable
sealed interface RecordDetailUiState {
    data object Loading : RecordDetailUiState
    data object NotFound : RecordDetailUiState
    data class Loaded(val record: Record) : RecordDetailUiState
}

sealed interface RecordDetailEvent {
    data object Deleted : RecordDetailEvent
}

internal fun ItemStatus.isWishlist(): Boolean = this == ItemStatus.WISHLIST