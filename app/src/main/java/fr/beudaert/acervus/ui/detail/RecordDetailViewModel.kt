package fr.beudaert.acervus.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordStatus
import fr.beudaert.acervus.domain.usecase.DeleteRecordUseCase
import fr.beudaert.acervus.domain.usecase.MarkAsPlayedUseCase
import fr.beudaert.acervus.domain.usecase.ObserveRecordByIdUseCase
import fr.beudaert.acervus.domain.usecase.ResetLastPlayedUseCase
import fr.beudaert.acervus.domain.usecase.ToggleWishlistUseCase
import fr.beudaert.acervus.domain.usecase.UpdateRatingUseCase
import fr.beudaert.acervus.ui.navigation.AcervusNavArgs
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
        checkNotNull(savedStateHandle.get<String>(AcervusNavArgs.RECORD_ID)) {
            "Missing nav argument: ${AcervusNavArgs.RECORD_ID}"
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

internal fun RecordStatus.isWishlist(): Boolean = this == RecordStatus.WISHLIST