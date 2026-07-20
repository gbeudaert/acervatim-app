package app.acervatim.ui.series

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.usecase.BulkEditDelta
import app.acervatim.domain.usecase.BulkEditResult
import app.acervatim.domain.usecase.DeleteRecordsUseCase
import app.acervatim.domain.usecase.DeleteResult
import app.acervatim.domain.usecase.DeleteSeriesUseCase
import app.acervatim.domain.usecase.ObserveSeriesByIdUseCase
import app.acervatim.domain.usecase.ObserveSeriesItemsUseCase
import app.acervatim.domain.usecase.UpdateItemStatusUseCase
import app.acervatim.domain.usecase.UpdateRecordsUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSeries: ObserveSeriesByIdUseCase,
    observeItems: ObserveSeriesItemsUseCase,
    private val updateItemStatus: UpdateItemStatusUseCase,
    private val deleteSeries: DeleteSeriesUseCase,
    private val updateRecords: UpdateRecordsUseCase,
    private val deleteRecords: DeleteRecordsUseCase
) : ViewModel() {

    private val seriesId: String =
        checkNotNull(savedStateHandle.get<String>(AcervatimNavArgs.SERIES_ID)) {
            "Missing nav argument: ${AcervatimNavArgs.SERIES_ID}"
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

    private val _events = MutableStateFlow<SeriesDetailEvent?>(null)
    val events: StateFlow<SeriesDetailEvent?> = _events.asStateFlow()

    fun onCycleStatus(itemId: String, current: ItemStatus) = viewModelScope.launch {
        updateItemStatus(itemId, nextStatusInCycle(current))
    }

    fun onSetStatus(itemId: String, target: ItemStatus) = viewModelScope.launch {
        updateItemStatus(itemId, target)
    }

    /**
     * Supprime la série. [cascade] = true supprime aussi tous les tomes rattachés ; false ne supprime
     * que le nœud série et laisse les tomes orphelins (FK `ON DELETE SET NULL`). Le choix est fait par
     * l'utilisateur dans le dialog de confirmation (cf. `spec-edition-multiple.md` §2.4).
     */
    fun onDelete(cascade: Boolean) = viewModelScope.launch {
        deleteSeries(seriesId, cascade)
        _events.value = SeriesDetailEvent.Deleted
    }

    fun onEventConsumed() {
        _events.value = null
    }

    // ---- Mode sélection + édition groupée des tomes (§2.1 / §2.2, lot L4) -----------------------

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val selectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun enterSelection(id: String) {
        _selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: String) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun selectAllVisible() {
        val loaded = uiState.value as? SeriesDetailUiState.Loaded ?: return
        _selectedIds.value = loaded.volumes.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    private val _bulkEditSheetOpen = MutableStateFlow(false)
    val bulkEditSheetOpen: StateFlow<Boolean> = _bulkEditSheetOpen.asStateFlow()

    private val _bulkEditResult = MutableStateFlow<BulkEditResult?>(null)
    val bulkEditResult: StateFlow<BulkEditResult?> = _bulkEditResult.asStateFlow()

    fun openBulkEdit() {
        if (_selectedIds.value.isNotEmpty()) _bulkEditSheetOpen.value = true
    }

    fun closeBulkEdit() {
        _bulkEditSheetOpen.value = false
    }

    fun applyBulkEdit(delta: BulkEditDelta) {
        val ids = _selectedIds.value
        _bulkEditSheetOpen.value = false
        if (delta.isEmpty || ids.isEmpty()) {
            clearSelection()
            return
        }
        viewModelScope.launch {
            _bulkEditResult.value = updateRecords(ids, delta)
            clearSelection()
        }
    }

    fun consumeBulkEditResult() {
        _bulkEditResult.value = null
    }

    private val _bulkDeleteResult = MutableStateFlow<DeleteResult?>(null)
    val bulkDeleteResult: StateFlow<DeleteResult?> = _bulkDeleteResult.asStateFlow()

    /** Supprime les tomes sélectionnés (des entrées : jamais de cascade série ici). L'UI confirme avant. */
    fun applyBulkDelete() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) {
            clearSelection()
            return
        }
        viewModelScope.launch {
            _bulkDeleteResult.value = deleteRecords(recordIds = ids)
            clearSelection()
        }
    }

    fun consumeBulkDeleteResult() {
        _bulkDeleteResult.value = null
    }
}

sealed interface SeriesDetailEvent {
    data object Deleted : SeriesDetailEvent
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
