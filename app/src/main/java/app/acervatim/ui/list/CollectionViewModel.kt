package app.acervatim.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.model.sync.SyncStatus
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.model.StatusFilter
import app.acervatim.domain.usecase.CollectionStats
import app.acervatim.domain.usecase.CollectionTypeStats
import app.acervatim.domain.usecase.ObserveActiveCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveCollectionEntriesUseCase
import app.acervatim.domain.usecase.ObserveCollectionStatsUseCase
import app.acervatim.domain.usecase.ObserveCollectionTypeStatsUseCase
import app.acervatim.domain.usecase.ObserveDistinctGenresUseCase
import app.acervatim.domain.usecase.ObserveListModeForCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveSeriesWithCountsUseCase
import app.acervatim.domain.usecase.ObserveVisibleCollectionTypesUseCase
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.SyncRepository
import app.acervatim.domain.usecase.SetActiveCollectionTypeUseCase
import app.acervatim.domain.usecase.SetListModeForCollectionTypeUseCase
import app.acervatim.ui.util.sectionLetterOf
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
import kotlinx.coroutines.flow.map
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
    observeVisibleCollectionTypes: ObserveVisibleCollectionTypesUseCase,
    observeListMode: ObserveListModeForCollectionTypeUseCase,
    observeGenres: ObserveDistinctGenresUseCase,
    observeStats: ObserveCollectionStatsUseCase,
    observeCollectionTypeStats: ObserveCollectionTypeStatsUseCase,
    private val setActiveCollectionType: SetActiveCollectionTypeUseCase,
    private val setListMode: SetListModeForCollectionTypeUseCase,
    authRepository: AuthRepository,
    syncRepository: SyncRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    /**
     * En-tête compte du drawer : lecture seule des états déjà en mémoire (aucun fetch déclenché
     * ici — le drawer ne doit pas coûter un appel réseau).
     */
    val accountHeader: StateFlow<AccountHeaderUiState> = combine(
        authRepository.authState,
        syncRepository.status,
        accountRepository.accountInfo
    ) { auth, sync, account ->
        AccountHeaderUiState(
            signedIn = auth is AuthState.SignedIn,
            syncStatus = sync,
            isPremium = (account as? AccountInfo.Known)?.profile?.premium?.isPremium
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountHeaderUiState())

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

    /**
     * Types affichés dans le drawer (configurés depuis Réglages → Collections affichées).
     * L'ordre suit [CollectionType.entries]. Toujours au moins un type (cf. SettingsRepository).
     */
    val visibleCollectionTypes: StateFlow<List<CollectionType>> = observeVisibleCollectionTypes()
        .map { visible -> CollectionType.entries.filter { it in visible } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionType.entries.toList())

    init {
        // Si le type actif vient d'être masqué, on bascule sur le premier type encore visible
        // pour ne jamais afficher une collection retirée du menu.
        viewModelScope.launch {
            combine(activeCollectionType, visibleCollectionTypes) { active, visible ->
                active to visible
            }.collect { (active, visible) ->
                if (visible.isNotEmpty() && active !in visible) {
                    setActiveCollectionType(visible.first())
                }
            }
        }
    }

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
            if (filter.listMode == ListMode.SERIAL) observeSeriesWithCounts(filter.collectionType ?: CollectionType.VINYL, filter.query)
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

    // ---- Mode sélection (§2.1) -----------------------------------------------------------------

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Ids sélectionnés en mode sélection. **Homogène par vue** (§9) : ids d'entrées en liste plate,
     * ids de séries en vue séries — les deux ne coexistent pas (vues distinctes). Vide ⇔ hors sélection.
     */
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    /** `true` dès qu'au moins une cible est sélectionnée (mode sélection actif). */
    val selectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Changer de collection active vide la sélection : ses ids appartiennent à l'ancienne vue.
        // (StateFlow déjà conflaté : chaque type distinct déclenche un clear.)
        viewModelScope.launch {
            activeCollectionType.collect { clearSelection() }
        }
    }

    /** Entre en mode sélection sur une cible (appui long). */
    fun enterSelection(id: String) {
        _selectedIds.value = setOf(id)
    }

    /** Coche/décoche une cible (tap en mode sélection). Décocher la dernière quitte le mode. */
    fun toggleSelection(id: String) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    /** Sélectionne toutes les cibles visibles de la vue courante (entrées ou séries). */
    fun selectAllVisible() {
        _selectedIds.value = visibleSelectableIds()
    }

    /** Quitte le mode sélection (croix / BackHandler / « Tout désélectionner »). */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Ids sélectionnables de la vue courante, selon le mode d'affichage (entrées vs séries). */
    private fun visibleSelectableIds(): Set<String> = when (val s = uiState.value) {
        is CollectionUiState.FlatContent ->
            s.sections.asSequence().flatMap { it.entries }.map { it.record.id }.toSet()
        is CollectionUiState.SerialContent ->
            s.seriesList.asSequence().map { it.series.id }.toSet()
        else -> emptySet()
    }

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

/** État de l'en-tête compte du drawer. `isPremium == null` ⇒ inconnu (déconnecté ou backend muet). */
@Immutable
data class AccountHeaderUiState(
    val signedIn: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val isPremium: Boolean? = null
)

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
