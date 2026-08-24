package app.acervatim.ui.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.defaultDisplayMode
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.model.sync.SyncStatus
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.RecordSortField
import app.acervatim.domain.model.naturalDirection
import app.acervatim.domain.model.hasLetterSections
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.model.StatusFilter
import app.acervatim.domain.usecase.CollectionStats
import app.acervatim.domain.usecase.CollectionTypeStats
import app.acervatim.domain.usecase.ObserveActiveCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveCollectionEntriesUseCase
import app.acervatim.domain.usecase.ObserveCollectionStatsUseCase
import app.acervatim.domain.usecase.ObserveCollectionTypeStatsUseCase
import app.acervatim.domain.usecase.ObserveDistinctGenresUseCase
import app.acervatim.domain.usecase.ObserveDisplayModeForCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveFilteredRecordsUseCase
import app.acervatim.domain.usecase.ObserveListModeForCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveSeriesWithCountsUseCase
import app.acervatim.domain.usecase.ObserveSortForCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveVisibleCollectionTypesUseCase
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.SyncRepository
import app.acervatim.domain.usecase.BulkEditDelta
import app.acervatim.domain.usecase.BulkEditResult
import app.acervatim.domain.usecase.DeleteRecordsUseCase
import app.acervatim.domain.usecase.DeleteResult
import app.acervatim.domain.usecase.RefreshMetadataUseCase
import app.acervatim.domain.usecase.RefreshOptions
import app.acervatim.domain.usecase.RefreshResult
import app.acervatim.domain.usecase.SetActiveCollectionTypeUseCase
import app.acervatim.domain.usecase.SetDisplayModeForCollectionTypeUseCase
import app.acervatim.domain.usecase.SetListModeForCollectionTypeUseCase
import app.acervatim.domain.usecase.SetSortForCollectionTypeUseCase
import app.acervatim.domain.usecase.UpdateRecordsUseCase
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
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    observeCollectionEntries: ObserveCollectionEntriesUseCase,
    observeFilteredRecords: ObserveFilteredRecordsUseCase,
    observeSeriesWithCounts: ObserveSeriesWithCountsUseCase,
    observeActiveCollectionType: ObserveActiveCollectionTypeUseCase,
    observeVisibleCollectionTypes: ObserveVisibleCollectionTypesUseCase,
    observeListMode: ObserveListModeForCollectionTypeUseCase,
    observeDisplayMode: ObserveDisplayModeForCollectionTypeUseCase,
    observeSort: ObserveSortForCollectionTypeUseCase,
    observeGenres: ObserveDistinctGenresUseCase,
    observeStats: ObserveCollectionStatsUseCase,
    observeCollectionTypeStats: ObserveCollectionTypeStatsUseCase,
    private val setActiveCollectionType: SetActiveCollectionTypeUseCase,
    private val setListMode: SetListModeForCollectionTypeUseCase,
    private val setDisplayMode: SetDisplayModeForCollectionTypeUseCase,
    private val setSort: SetSortForCollectionTypeUseCase,
    private val updateRecords: UpdateRecordsUseCase,
    private val deleteRecords: DeleteRecordsUseCase,
    private val refreshMetadata: RefreshMetadataUseCase,
    private val clock: Clock,
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
     * Densité d'affichage du type actif — axe **orthogonal** au [ListMode] : les deux se
     * combinent, chacun persisté par [CollectionType].
     */
    val displayMode: StateFlow<DisplayMode> = activeCollectionType
        .flatMapLatest { type -> observeDisplayMode(type) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CollectionType.VINYL.defaultDisplayMode
        )

    /**
     * Tri du type actif. Persisté par [CollectionType] comme les deux modes : c'est une
     * préférence d'affichage, pas un filtre — elle survit au bouton « réinitialiser ».
     */
    val sort: StateFlow<RecordSort> = activeCollectionType
        .flatMapLatest { type -> observeSort(type) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordSort.DEFAULT
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

    private data class ContentQuery(
        val filter: RecordFilter,
        val displayMode: DisplayMode,
        val sort: RecordSort
    )

    private val contentQuery: Flow<ContentQuery> =
        combine(effectiveFilter, displayMode, sort, ::ContentQuery)

    private val flatContentFlow: Flow<List<RecordArtistEntry>> =
        contentQuery.flatMapLatest { (filter, display, sort) ->
            // La liste plate est la seule vue à dupliquer un disque par artiste : grille et
            // galerie affichent une tuile par disque, elles lisent [tileRecordsFlow].
            if (filter.listMode == ListMode.FLAT && display == DisplayMode.LIST) {
                observeCollectionEntries(filter, sort)
            } else {
                flowOf(emptyList())
            }
        }

    /** Disques bruts (un par item, sans duplication par artiste) — grille et galerie. */
    private val tileRecordsFlow: Flow<List<Record>> =
        contentQuery.flatMapLatest { (filter, display, sort) ->
            if (filter.listMode == ListMode.FLAT && display != DisplayMode.LIST) {
                observeFilteredRecords(filter, sort)
            } else {
                flowOf(emptyList())
            }
        }

    private val serialContentFlow: Flow<List<SeriesWithCounts>> =
        effectiveFilter.flatMapLatest { filter ->
            if (filter.listMode == ListMode.SERIAL) observeSeriesWithCounts(filter.collectionType ?: CollectionType.VINYL, filter.query)
            else flowOf(emptyList())
        }

    private val statsFlow: Flow<CollectionStats> =
        activeCollectionType.flatMapLatest { type -> observeStats(type) }

    /** Entrées combinées en deux temps : `combine` typé s'arrête à cinq flux. */
    private data class ContentInputs(
        val filter: RecordFilter,
        val entries: List<RecordArtistEntry>,
        val series: List<SeriesWithCounts>,
        val stats: CollectionStats,
        val sort: RecordSort
    )

    private val contentInputs: Flow<ContentInputs> = combine(
        effectiveFilter,
        flatContentFlow,
        serialContentFlow,
        statsFlow,
        sort,
        ::ContentInputs
    )

    val uiState: StateFlow<CollectionUiState> = combine(
        contentInputs,
        tileRecordsFlow,
        displayMode
    ) { inputs, records, display ->
        CollectionUiState.from(
            filter = inputs.filter,
            entries = inputs.entries,
            series = inputs.series,
            records = records,
            stats = inputs.stats,
            displayMode = display,
            sort = inputs.sort,
            now = clock.instant()
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

    // ---- Édition groupée (§2.2, lot L3) --------------------------------------------------------

    private val _bulkEditSheetOpen = MutableStateFlow(false)
    val bulkEditSheetOpen: StateFlow<Boolean> = _bulkEditSheetOpen.asStateFlow()

    /** Bilan de la dernière édition groupée, à consommer par un Snackbar (`null` = rien à afficher). */
    private val _bulkEditResult = MutableStateFlow<BulkEditResult?>(null)
    val bulkEditResult: StateFlow<BulkEditResult?> = _bulkEditResult.asStateFlow()

    fun openBulkEdit() {
        if (_selectedIds.value.isNotEmpty()) _bulkEditSheetOpen.value = true
    }

    fun closeBulkEdit() {
        _bulkEditSheetOpen.value = false
    }

    /** Applique le delta aux entrées sélectionnées, ferme la feuille et sort du mode sélection. */
    fun applyBulkEdit(delta: BulkEditDelta) {
        val ids = _selectedIds.value
        _bulkEditSheetOpen.value = false
        if (delta.isEmpty || ids.isEmpty()) {
            clearSelection()
            return
        }
        viewModelScope.launch {
            val result = updateRecords(ids, delta)
            _bulkEditResult.value = result
            clearSelection()
        }
    }

    /** Le Snackbar de bilan a été affiché : on réarme l'état. */
    fun consumeBulkEditResult() {
        _bulkEditResult.value = null
    }

    // ---- Suppression groupée (§2.4, lot L5) ----------------------------------------------------

    private val _bulkDeleteResult = MutableStateFlow<DeleteResult?>(null)
    val bulkDeleteResult: StateFlow<DeleteResult?> = _bulkDeleteResult.asStateFlow()

    /**
     * Supprime la sélection courante. En vue séries la sélection porte des **séries** → mode
     * [cascade] (cascade/orphelins, §2.4) ; sinon elle porte des **entrées** ([cascade] ignoré).
     * L'UI confirme **avant** d'appeler (§2.4).
     */
    fun applyBulkDelete(cascade: Boolean) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) {
            clearSelection()
            return
        }
        val serial = uiState.value is CollectionUiState.SerialContent
        viewModelScope.launch {
            _bulkDeleteResult.value = if (serial) {
                deleteRecords(seriesIds = ids, cascade = cascade)
            } else {
                deleteRecords(recordIds = ids)
            }
            clearSelection()
        }
    }

    fun consumeBulkDeleteResult() {
        _bulkDeleteResult.value = null
    }

    // ---- Rafraîchissement groupé (§2.5, lot L7) ------------------------------------------------

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshResult = MutableStateFlow<RefreshResult?>(null)
    val refreshResult: StateFlow<RefreshResult?> = _refreshResult.asStateFlow()

    /**
     * Rafraîchit jaquettes/descriptions de la sélection. Vue séries → cibles **séries** (1 appel
     * edition-mapping chacune) ; sinon **entrées**. [overwrite] force le remplacement (§2.5.2).
     */
    fun applyRefresh(overwrite: Boolean) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) {
            clearSelection()
            return
        }
        val serial = uiState.value is CollectionUiState.SerialContent
        viewModelScope.launch {
            _refreshing.value = true
            _refreshResult.value = if (serial) {
                refreshMetadata(seriesIds = ids, options = RefreshOptions(overwrite))
            } else {
                refreshMetadata(recordIds = ids, options = RefreshOptions(overwrite))
            }
            _refreshing.value = false
            clearSelection()
        }
    }

    fun consumeRefreshResult() {
        _refreshResult.value = null
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

    /**
     * Bascule la densité d'affichage du type actif (liste → grille → galerie). Persistée par
     * type : revenir sur une collection retrouve la vue qu'on y avait laissée.
     */
    fun onDisplayModeSelected(mode: DisplayMode) {
        viewModelScope.launch {
            setDisplayMode(activeCollectionType.value, mode)
        }
    }

    /**
     * Choisir un champ déjà actif **inverse** le sens ; en choisir un autre part de son sens
     * naturel (dernières écoutes et meilleures notes en tête, artistes de A à Z). Sans ça,
     * demander « les mieux notés » afficherait d'abord les une étoile.
     */
    fun onSortFieldSelected(field: RecordSortField) {
        val current = sort.value
        val next = if (current.field == field) {
            current.copy(direction = current.direction.toggled())
        } else {
            RecordSort(field = field, direction = field.naturalDirection)
        }
        viewModelScope.launch {
            setSort(activeCollectionType.value, next)
        }
    }

    fun onSortDirectionToggled() {
        val current = sort.value
        viewModelScope.launch {
            setSort(activeCollectionType.value, current.copy(direction = current.direction.toggled()))
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

/**
 * Tranche de liste plate. [letter] est **nulle** dès que l'ordre n'est pas alphabétique : sur un
 * tri par année ou par dernière écoute, un en-tête « A » annoncerait un classement qui n'existe
 * pas. La liste n'affiche alors aucun en-tête et reste d'un seul tenant.
 */
@Immutable
data class CollectionSection(val letter: String?, val entries: List<RecordArtistEntry>)

/**
 * Tuile de jaquette — la maille commune de la grille et de la galerie. Un disque, un tome ou une
 * série s'y projettent de la même façon : c'est ce qui permet aux deux vues (plate et séries) de
 * partager les mêmes composants au lieu de dupliquer un écran par type de collection.
 */
@Immutable
data class CollectionTile(
    val id: String,
    val title: String,
    val subtitle: String?,
    val coverPath: String?,
    val coverUrl: String?,
    /** Clé de cache mémoire de la jaquette locale (préfixée pour ne pas confondre série et disque). */
    val cacheKey: String,
    val playCount: Int
)

/**
 * Rubrique d'une vue en tuiles. Seule la **nature** de la rubrique remonte ici : le libellé est
 * posé par l'UI (`stringResource`), aucun texte visible ne sort du ViewModel.
 */
enum class TileSectionKind {
    /** Toute la collection, sans en-tête : c'est la grille. */
    ALL,
    RECENTLY_PLAYED,
    MOST_PLAYED,

    /** Jamais écouté, ou plus depuis six mois. */
    FORGOTTEN
}

@Immutable
data class TileSection(val kind: TileSectionKind, val tiles: List<CollectionTile>)

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
        val hasActiveFilters: Boolean,
        /**
         * [DisplayMode.GRID] (défaut historique de la vue séries) ou [DisplayMode.LIST]. La
         * galerie, elle, passe par [TileContent] : ses rubriques ne sont pas une liste de séries.
         */
        val displayMode: DisplayMode = DisplayMode.GRID
    ) : CollectionUiState

    /**
     * Vue en tuiles : grille (une seule rubrique [TileSectionKind.ALL], sans en-tête) ou galerie
     * (rubriques d'écoute). Même état pour la collection plate et pour les séries — [serial] ne
     * sert qu'à router le clic vers le bon écran.
     */
    @Immutable
    data class TileContent(
        val activeCollectionType: CollectionType,
        val displayMode: DisplayMode,
        val serial: Boolean,
        val sections: List<TileSection>,
        val ownedCount: Int,
        val wishlistCount: Int,
        val hasActiveFilters: Boolean
    ) : CollectionUiState

    companion object {

        /** Taille d'une rubrique de galerie : une mise en avant, pas un catalogue. */
        const val GALLERY_SECTION_SIZE = 12

        /** Au-delà, un item est « oublié » — même seuil que le filtre `notPlayedSinceSixMonths`. */
        private const val FORGOTTEN_AFTER_DAYS = 182L

        /**
         * Découpe la liste en sections **sans jamais la réordonner** : les entrées arrivent déjà
         * dans l'ordre du tri (SQL en local, comparateur ailleurs), un regroupement qui trierait
         * ses clés écraserait ce choix — c'était le cas tant que l'ordre alphabétique était le
         * seul possible.
         *
         * La lettre vient de l'artiste ou du titre selon le champ trié : sur un tri par titre,
         * des en-têtes tirés du nom d'artiste ne suivraient plus l'ordre affiché.
         */
        private fun sectionsFor(
            entries: List<RecordArtistEntry>,
            sort: RecordSort
        ): List<CollectionSection> {
            if (!sort.field.hasLetterSections) {
                return listOf(CollectionSection(letter = null, entries = entries))
            }
            return entries
                .groupBy {
                    val source = if (sort.field == RecordSortField.TITLE) {
                        it.record.title
                    } else {
                        it.displayedArtist.name
                    }
                    sectionLetterOf(source)
                }
                .map { (letter, list) -> CollectionSection(letter = letter, entries = list) }
        }

        fun from(
            filter: RecordFilter,
            entries: List<RecordArtistEntry>,
            series: List<SeriesWithCounts>,
            records: List<Record>,
            stats: CollectionStats,
            displayMode: DisplayMode,
            sort: RecordSort,
            now: Instant
        ): CollectionUiState {
            val hasFilters = filter.isAnyAdvancedFilterActive || filter.query.isNotBlank()
            val type = filter.collectionType ?: CollectionType.VINYL
            val serial = filter.listMode == ListMode.SERIAL
            val playable =
                if (serial) series.map { it.toPlayable() } else records.map { it.toPlayable() }

            fun empty() = Empty(
                activeCollectionType = type,
                listMode = filter.listMode,
                ownedCount = stats.ownedCount,
                wishlistCount = stats.wishlistCount,
                hasActiveFilters = hasFilters
            )

            return when {
                // Liste plate : la seule vue qui duplique un disque par artiste (sections par lettre).
                !serial && displayMode == DisplayMode.LIST -> {
                    if (entries.isEmpty()) {
                        empty()
                    } else {
                        FlatContent(
                            activeCollectionType = type,
                            sections = sectionsFor(entries, sort),
                            ownedCount = stats.ownedCount,
                            wishlistCount = stats.wishlistCount,
                            hasActiveFilters = hasFilters
                        )
                    }
                }
                // Séries en liste ou en grille : la maille reste la série, pas la tuile générique.
                serial && displayMode != DisplayMode.GALLERY -> {
                    if (series.isEmpty()) {
                        empty()
                    } else {
                        SerialContent(
                            activeCollectionType = type,
                            seriesList = series,
                            ownedCount = stats.ownedCount,
                            wishlistCount = stats.wishlistCount,
                            hasActiveFilters = hasFilters,
                            displayMode = displayMode
                        )
                    }
                }
                else -> {
                    if (playable.isEmpty()) {
                        empty()
                    } else {
                        TileContent(
                            activeCollectionType = type,
                            displayMode = displayMode,
                            serial = serial,
                            sections = if (displayMode == DisplayMode.GALLERY) {
                                gallerySections(playable, now)
                            } else {
                                listOf(TileSection(TileSectionKind.ALL, playable.map { it.tile }))
                            },
                            ownedCount = stats.ownedCount,
                            wishlistCount = stats.wishlistCount,
                            hasActiveFilters = hasFilters
                        )
                    }
                }
            }
        }

        /**
         * Rubriques de la galerie. Une rubrique vide est **absente** de la liste : une collection
         * neuve n'a aucune écoute, et un bandeau vide sous un titre ne dit rien à personne.
         *
         * Les trois rubriques d'écoute sont des mises en avant bornées à [GALLERY_SECTION_SIZE] ;
         * [TileSectionKind.ALL] ferme la galerie avec la collection entière, sans quoi la vue
         * cacherait la majorité des items.
         */
        private fun gallerySections(playable: List<Playable>, now: Instant): List<TileSection> {
            val forgottenBefore = now.minus(FORGOTTEN_AFTER_DAYS, ChronoUnit.DAYS)
            val recent = playable
                .filter { it.lastPlayedAt != null }
                .sortedByDescending { it.lastPlayedAt }
                .take(GALLERY_SECTION_SIZE)
            val most = playable
                .filter { it.playCount > 0 }
                .sortedWith(
                    compareByDescending<Playable> { it.playCount }
                        .thenByDescending { it.lastPlayedAt ?: Instant.EPOCH }
                )
                .take(GALLERY_SECTION_SIZE)
            val forgotten = playable
                .filter { it.lastPlayedAt == null || it.lastPlayedAt.isBefore(forgottenBefore) }
                .take(GALLERY_SECTION_SIZE)
            return listOf(
                TileSection(TileSectionKind.RECENTLY_PLAYED, recent.map { it.tile }),
                TileSection(TileSectionKind.MOST_PLAYED, most.map { it.tile }),
                TileSection(TileSectionKind.FORGOTTEN, forgotten.map { it.tile }),
                TileSection(TileSectionKind.ALL, playable.map { it.tile })
            ).filter { it.tiles.isNotEmpty() }
        }

        /** Tuile + les deux données d'écoute qui décident des rubriques. */
        private data class Playable(
            val tile: CollectionTile,
            val lastPlayedAt: Instant?,
            val playCount: Int
        )

        private fun Record.toPlayable(): Playable = Playable(
            tile = CollectionTile(
                id = id,
                title = title,
                subtitle = primaryArtist?.name,
                coverPath = coverPath,
                coverUrl = coverRemoteUrl,
                cacheKey = id,
                playCount = playCount
            ),
            lastPlayedAt = lastPlayedAt,
            playCount = playCount
        )

        private fun SeriesWithCounts.toPlayable(): Playable = Playable(
            tile = CollectionTile(
                id = series.id,
                title = series.title,
                subtitle = series.originalAuthor,
                coverPath = series.coverPath,
                coverUrl = series.coverRemoteUrl,
                cacheKey = "series-${series.id}",
                playCount = playCount
            ),
            lastPlayedAt = lastPlayedAt,
            playCount = playCount
        )
    }
}
