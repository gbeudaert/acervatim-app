package fr.beudaert.acervatim.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervatim.domain.model.AcervatimFormat
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.ListMode
import fr.beudaert.acervatim.domain.model.RecordArtistEntry
import fr.beudaert.acervatim.domain.model.RecordFilter
import fr.beudaert.acervatim.domain.model.SeriesWithCounts
import fr.beudaert.acervatim.domain.model.StatusFilter
import fr.beudaert.acervatim.domain.usecase.CollectionStats
import fr.beudaert.acervatim.domain.usecase.CollectionTypeStats
import fr.beudaert.acervatim.domain.usecase.ObserveActiveCollectionTypeUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveCollectionEntriesUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveCollectionStatsUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveCollectionTypeStatsUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveDistinctGenresUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveListModeForCollectionTypeUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveSeriesWithCountsUseCase
import fr.beudaert.acervatim.domain.usecase.SetActiveCollectionTypeUseCase
import fr.beudaert.acervatim.domain.usecase.SetListModeForCollectionTypeUseCase
import fr.beudaert.acervatim.ui.util.sectionLetterOf
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
    observeListMode: ObserveListModeForCollectionTypeUseCase,
    observeGenres: ObserveDistinctGenresUseCase,
    observeStats: ObserveCollectionStatsUseCase,
    observeCollectionTypeStats: ObserveCollectionTypeStatsUseCase,
    private val setActiveCollectionType: SetActiveCollectionTypeUseCase,
    private val setListMode: SetListModeForCollectionTypeUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Filtres saisis dans le bottom sheet (status / genre / format / non écoutés).
     * Le champ `listMode` interne est ignoré : le mode effectif est lu depuis DataStore
     * via [observeListMode], pour respecter la persistance par [CollectionType] (cf. §6
     * du contexte global). `advancedFilter` exposé en surcharge ce listMode.
     */
    private val _advancedFilter = MutableStateFlow(RecordFilter())

    private val _filterSheetOpen = MutableStateFlow(false)
    val filterSheetOpen: StateFlow<Boolean> = _filterSheetOpen.asStateFlow()

    val activeCollectionType: StateFlow<CollectionType> = observeActiveCollectionType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionType.VINYL)

    private val listModeForActiveType: StateFlow<ListMode> = activeCollectionType
        .flatMapLatest { type -> observeListMode(type) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CollectionType.VINYL.defaultListMode
        )

    /**
     * Filtre exposé à l'UI (FilterSheet) — `listMode` provient du DataStore persisté par
     * [CollectionType], `collectionType` du type actif, le reste de [_advancedFilter].
     */
    val advancedFilter: StateFlow<RecordFilter> = combine(
        _advancedFilter,
        listModeForActiveType,
        activeCollectionType
    ) { f, mode, type ->
        f.copy(listMode = mode, collectionType = type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordFilter())

    val collectionTypeStats: StateFlow<List<CollectionTypeStats>> = observeCollectionTypeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableGenres: StateFlow<List<String>> = observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val debouncedQuery: Flow<String> =
        _query.debounce { if (it.isEmpty()) 0L else QUERY_DEBOUNCE_MS }.distinctUntilChanged()

    private val effectiveFilter: StateFlow<RecordFilter> = combine(
        debouncedQuery,
        advancedFilter
    ) { q, f -> f.copy(query = q) }
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
     * Le [ListMode] suit automatiquement la valeur persistée pour le nouveau type
     * (via [observeListMode]), pas besoin de l'aligner ici.
     */
    fun onCollectionTypeSelected(type: CollectionType) {
        viewModelScope.launch {
            setActiveCollectionType(type)
        }
    }

    fun applyFilter(
        listMode: ListMode,
        status: StatusFilter,
        genre: String?,
        format: AcervatimFormat?,
        notPlayedSinceSixMonths: Boolean
    ) {
        viewModelScope.launch {
            setListMode(activeCollectionType.value, listMode)
        }
        _advancedFilter.update {
            it.copy(
                status = status,
                genre = genre,
                format = format,
                notPlayedSinceSixMonths = notPlayedSinceSixMonths
            )
        }
        _filterSheetOpen.value = false
    }

    /**
     * Réinitialise les filtres avancés. Le `listMode` n'est PAS reset ici : il reste piloté
     * par la valeur persistée pour le type actif (cf. écran Réglages → Vue par collection).
     */
    fun resetFilter() {
        _advancedFilter.update { current ->
            RecordFilter(query = current.query)
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
