package fr.beudaert.acervus.ui.series

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.usecase.ObserveSeriesByIdUseCase
import fr.beudaert.acervus.domain.usecase.ObserveSeriesItemsUseCase
import fr.beudaert.acervus.domain.usecase.UpdateItemStatusUseCase
import fr.beudaert.acervus.ui.navigation.AcervusNavArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSeries: ObserveSeriesByIdUseCase,
    observeItems: ObserveSeriesItemsUseCase,
    private val updateItemStatus: UpdateItemStatusUseCase
) : ViewModel() {

    private val seriesId: String =
        checkNotNull(savedStateHandle.get<String>(AcervusNavArgs.SERIES_ID)) {
            "Missing nav argument: ${AcervusNavArgs.SERIES_ID}"
        }

    val uiState: StateFlow<SeriesDetailUiState> = combine(
        observeSeries(seriesId),
        observeItems(seriesId)
    ) { series, items ->
        if (series == null) SeriesDetailUiState.NotFound
        else SeriesDetailUiState.Loaded(series = series, volumes = items)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SeriesDetailUiState.Loading
    )

    fun onCycleStatus(itemId: String, current: ItemStatus) = viewModelScope.launch {
        updateItemStatus(itemId, nextStatusInCycle(current))
    }

    fun onSetStatus(itemId: String, target: ItemStatus) = viewModelScope.launch {
        updateItemStatus(itemId, target)
    }
}

@Immutable
sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState
    data object NotFound : SeriesDetailUiState

    @Immutable
    data class Loaded(
        val series: Series,
        val volumes: List<Record>
    ) : SeriesDetailUiState
}

/** OWNED → WISHLIST → IGNORED → OWNED. */
internal fun nextStatusInCycle(current: ItemStatus): ItemStatus = when (current) {
    ItemStatus.OWNED -> ItemStatus.WISHLIST
    ItemStatus.WISHLIST -> ItemStatus.IGNORED
    ItemStatus.IGNORED -> ItemStatus.OWNED
}
