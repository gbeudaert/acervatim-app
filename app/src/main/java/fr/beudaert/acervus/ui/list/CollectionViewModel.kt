package fr.beudaert.acervus.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.ListMode
import fr.beudaert.acervus.domain.model.RecordArtistEntry
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.model.StatusFilter
import fr.beudaert.acervus.domain.usecase.CollectionStats
import fr.beudaert.acervus.domain.usecase.ObserveCollectionEntriesUseCase
import fr.beudaert.acervus.domain.usecase.ObserveCollectionStatsUseCase
import fr.beudaert.acervus.domain.usecase.ObserveDistinctGenresUseCase
import fr.beudaert.acervus.ui.util.sectionLetterOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    observeCollectionEntries: ObserveCollectionEntriesUseCase,
    observeGenres: ObserveDistinctGenresUseCase,
    observeStats: ObserveCollectionStatsUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _advancedFilter = MutableStateFlow(RecordFilter())
    val advancedFilter: StateFlow<RecordFilter> = _advancedFilter.asStateFlow()

    private val _filterSheetOpen = MutableStateFlow(false)
    val filterSheetOpen: StateFlow<Boolean> = _filterSheetOpen.asStateFlow()

    val availableGenres: StateFlow<List<String>> = observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val effectiveFilter: StateFlow<RecordFilter> = combine(
        _query.debounce { if (it.isEmpty()) 0L else QUERY_DEBOUNCE_MS }.distinctUntilChanged(),
        _advancedFilter
    ) { q, f -> f.copy(query = q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordFilter())

    val uiState: StateFlow<CollectionUiState> = combine(
        effectiveFilter.flatMapLatest { observeCollectionEntries(it) },
        observeStats(),
        _advancedFilter
    ) { entries, stats, filter ->
        CollectionUiState.from(entries = entries, stats = stats, filter = filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionUiState.Loading
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onClearQuery() {
        _query.value = ""
    }

    fun openFilterSheet() {
        _filterSheetOpen.value = true
    }

    fun closeFilterSheet() {
        _filterSheetOpen.value = false
    }

    fun applyFilter(
        listMode: ListMode,
        status: StatusFilter,
        genre: String?,
        format: AcervusFormat?,
        notPlayedSinceSixMonths: Boolean
    ) {
        _advancedFilter.update {
            it.copy(
                listMode = listMode,
                status = status,
                genre = genre,
                format = format,
                notPlayedSinceSixMonths = notPlayedSinceSixMonths
            )
        }
        _filterSheetOpen.value = false
    }

    fun resetFilter() {
        _advancedFilter.update { RecordFilter(query = it.query) }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 200L
    }
}

@Immutable
data class CollectionSection(val letter: String, val entries: List<RecordArtistEntry>)

@Immutable
sealed interface CollectionUiState {
    data object Loading : CollectionUiState

    @Immutable
    data class Empty(
        val ownedCount: Int,
        val wishlistCount: Int,
        val hasActiveFilters: Boolean
    ) : CollectionUiState

    @Immutable
    data class Content(
        val sections: List<CollectionSection>,
        val ownedCount: Int,
        val wishlistCount: Int,
        val hasActiveFilters: Boolean
    ) : CollectionUiState

    companion object {
        fun from(
            entries: List<RecordArtistEntry>,
            stats: CollectionStats,
            filter: RecordFilter
        ): CollectionUiState {
            val hasFilters = filter.isAnyAdvancedFilterActive || filter.query.isNotBlank()
            if (entries.isEmpty()) {
                return Empty(
                    ownedCount = stats.ownedCount,
                    wishlistCount = stats.wishlistCount,
                    hasActiveFilters = hasFilters
                )
            }
            val sections = entries
                .groupBy { sectionLetterOf(it.displayedArtist.name) }
                .toSortedMap()
                .map { (letter, list) -> CollectionSection(letter = letter, entries = list) }
            return Content(
                sections = sections,
                ownedCount = stats.ownedCount,
                wishlistCount = stats.wishlistCount,
                hasActiveFilters = hasFilters
            )
        }
    }
}
