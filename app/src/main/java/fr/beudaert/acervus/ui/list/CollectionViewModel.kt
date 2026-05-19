package fr.beudaert.acervus.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ListMode
import fr.beudaert.acervus.domain.model.RecordArtistEntry
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.model.SeriesWithCounts
import fr.beudaert.acervus.domain.model.StatusFilter
import fr.beudaert.acervus.domain.usecase.CollectionStats
import fr.beudaert.acervus.domain.usecase.CollectionTypeStats
import fr.beudaert.acervus.domain.usecase.ObserveActiveCollectionTypeUseCase
import fr.beudaert.acervus.domain.usecase.ObserveCollectionEntriesUseCase
import fr.beudaert.acervus.domain.usecase.ObserveCollectionStatsUseCase
import fr.beudaert.acervus.domain.usecase.ObserveCollectionTypeStatsUseCase
import fr.beudaert.acervus.domain.usecase.ObserveDistinctGenresUseCase
import fr.beudaert.acervus.domain.usecase.ObserveSeriesWithCountsUseCase
import fr.beudaert.acervus.domain.usecase.SetActiveCollectionTypeUseCase
import fr.beudaert.acervus.ui.util.sectionLetterOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    observeCollectionEntries: ObserveCollectionEntriesUseCase,
    observeSeriesWithCounts: ObserveSeriesWithCountsUseCase,
    observeActiveCollectionType: ObserveActiveCollectionTypeUseCase,
    observeGenres: ObserveDistinctGenresUseCase,
    observeStats: ObserveCollectionStatsUseCase,
    observeCollectionTypeStats: ObserveCollectionTypeStatsUseCase,
    private val setActiveCollectionType: SetActiveCollectionTypeUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _advancedFilter = MutableStateFlow(RecordFilter())
    val advancedFilter: StateFlow<RecordFilter> = _advancedFilter.asStateFlow()

    private val _filterSheetOpen = MutableStateFlow(false)
    val filterSheetOpen: StateFlow<Boolean> = _filterSheetOpen.asStateFlow()

    val activeCollectionType: StateFlow<CollectionType> = observeActiveCollectionType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionType.VINYL)

    val collectionTypeStats: StateFlow<List<CollectionTypeStats>> = observeCollectionTypeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableGenres: StateFlow<List<String>> = observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val debouncedQuery: Flow<String> =
        _query.debounce { if (it.isEmpty()) 0L else QUERY_DEBOUNCE_MS }.distinctUntilChanged()

    /**
     * Filtre effectif : combine query (débouncé) + advancedFilter + collectionType actif.
     * Le `listMode` provient toujours d'`_advancedFilter` (peut être surchargé par l'utilisateur),
     * mais [onCollectionTypeSelected] réinitialise au défaut du nouveau type.
     */
    private val effectiveFilter: StateFlow<RecordFilter> = combine(
        debouncedQuery,
        _advancedFilter,
        activeCollectionType
    ) { q, f, type -> f.copy(query = q, collectionType = type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordFilter())

    private val flatContentFlow: Flow<List<RecordArtistEntry>> =
        effectiveFilter.flatMapLatest { filter ->
            if (filter.listMode == ListMode.FLAT) observeCollectionEntries(filter)
            else flowOf(emptyList())
        }

    private val serialContentFlow: Flow<List<SeriesWithCounts>> =
        effectiveFilter.flatMapLatest { filter ->
            if (filter.listMode == ListMode.SERIAL) observeSeriesWithCounts(filter.collectionType ?: CollectionType.VINYL)
            else flowOf(emptyList())
        }

    private val statsFlow: Flow<CollectionStats> =
        activeCollectionType.flatMapLatest { type -> observeStats(type) }

    val uiState: StateFlow<CollectionUiState> = combine(
        effectiveFilter,
        flatContentFlow,
        serialContentFlow,
        statsFlow
    ) { filter, entries, series, stats ->
        CollectionUiState.from(
            filter = filter,
            entries = entries,
            series = series,
            stats = stats
        )
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

    /**
     * Bascule de collection active depuis le drawer.
     * Aligne aussi `listMode` sur le défaut du nouveau type (manga → SERIAL, vinyle → FLAT) ;
     * l'utilisateur peut le surcharger ensuite via le FilterSheet.
     */
    fun onCollectionTypeSelected(type: CollectionType) {
        viewModelScope.launch {
            setActiveCollectionType(type)
            _advancedFilter.update { it.copy(listMode = type.defaultListMode) }
        }
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
        _advancedFilter.update { current ->
            RecordFilter(query = current.query, listMode = current.listMode)
        }
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
        val activeCollectionType: CollectionType,
        val listMode: ListMode,
        val ownedCount: Int,
        val wishlistCount: Int,
        val hasActiveFilters: Boolean
    ) : CollectionUiState

    @Immutable
    data class FlatContent(
        val activeCollectionType: CollectionType,
        val sections: List<CollectionSection>,
        val ownedCount: Int,
        val wishlistCount: Int,
        val hasActiveFilters: Boolean
    ) : CollectionUiState

    @Immutable
    data class SerialContent(
        val activeCollectionType: CollectionType,
        val seriesList: List<SeriesWithCounts>,
        val ownedCount: Int,
        val wishlistCount: Int,
        val hasActiveFilters: Boolean
    ) : CollectionUiState

    companion object {
        fun from(
            filter: RecordFilter,
            entries: List<RecordArtistEntry>,
            series: List<SeriesWithCounts>,
            stats: CollectionStats
        ): CollectionUiState {
            val hasFilters = filter.isAnyAdvancedFilterActive || filter.query.isNotBlank()
            val type = filter.collectionType ?: CollectionType.VINYL
            return when (filter.listMode) {
                ListMode.FLAT -> {
                    if (entries.isEmpty()) {
                        Empty(
                            activeCollectionType = type,
                            listMode = ListMode.FLAT,
                            ownedCount = stats.ownedCount,
                            wishlistCount = stats.wishlistCount,
                            hasActiveFilters = hasFilters
                        )
                    } else {
                        val sections = entries
                            .groupBy { sectionLetterOf(it.displayedArtist.name) }
                            .toSortedMap()
                            .map { (letter, list) -> CollectionSection(letter = letter, entries = list) }
                        FlatContent(
                            activeCollectionType = type,
                            sections = sections,
                            ownedCount = stats.ownedCount,
                            wishlistCount = stats.wishlistCount,
                            hasActiveFilters = hasFilters
                        )
                    }
                }
                ListMode.SERIAL -> {
                    if (series.isEmpty()) {
                        Empty(
                            activeCollectionType = type,
                            listMode = ListMode.SERIAL,
                            ownedCount = stats.ownedCount,
                            wishlistCount = stats.wishlistCount,
                            hasActiveFilters = hasFilters
                        )
                    } else {
                        SerialContent(
                            activeCollectionType = type,
                            seriesList = series,
                            ownedCount = stats.ownedCount,
                            wishlistCount = stats.wishlistCount,
                            hasActiveFilters = hasFilters
                        )
                    }
                }
            }
        }
    }
}
