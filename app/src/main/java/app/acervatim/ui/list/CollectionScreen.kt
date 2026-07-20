package app.acervatim.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.StatusFilter
import app.acervatim.domain.usecase.CollectionTypeStats
import app.acervatim.ui.components.RecordRow
import app.acervatim.ui.theme.AcervatimTheme
import kotlinx.coroutines.launch

@Composable
fun CollectionScreen(
    onAddRecord: () -> Unit,
    onScanRecord: (CollectionType) -> Unit,
    onScanSeries: (CollectionType) -> Unit,
    onAddManga: () -> Unit,
    onAddSeries: (CollectionType) -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.advancedFilter.collectAsStateWithLifecycle()
    val sheetOpen by viewModel.filterSheetOpen.collectAsStateWithLifecycle()
    val genres by viewModel.availableGenres.collectAsStateWithLifecycle()
    val activeType by viewModel.activeCollectionType.collectAsStateWithLifecycle()
    val visibleTypes by viewModel.visibleCollectionTypes.collectAsStateWithLifecycle()
    val typeStats by viewModel.collectionTypeStats.collectAsStateWithLifecycle()
    val accountHeader by viewModel.accountHeader.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    CollectionScreenContent(
        state = state,
        query = query,
        filter = filter,
        activeCollectionType = activeType,
        visibleCollectionTypes = visibleTypes,
        collectionTypeStats = typeStats,
        accountHeader = accountHeader,
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onOpenFilters = viewModel::openFilterSheet,
        onOpenSettings = onOpenSettings,
        onOpenAccount = onOpenAccount,
        onCollectionTypeSelected = viewModel::onCollectionTypeSelected,
        onAddRecord = onAddRecord,
        onScanRecord = { onScanRecord(activeType) },
        onScanSeries = { onScanSeries(activeType) },
        onAddManga = onAddManga,
        onAddSeries = { onAddSeries(activeType) },
        onRecordClick = onOpenRecord,
        onSeriesClick = onOpenSeries,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAllVisible,
        onClearSelection = viewModel::clearSelection
    )

    if (sheetOpen) {
        FilterSheet(
            initial = filter,
            activeCollectionType = activeType,
            availableGenres = genres,
            onApply = viewModel::applyFilter,
            onReset = viewModel::resetFilter,
            onDismiss = viewModel::closeFilterSheet
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionScreenContent(
    state: CollectionUiState,
    query: String,
    filter: RecordFilter,
    activeCollectionType: CollectionType,
    visibleCollectionTypes: List<CollectionType>,
    collectionTypeStats: List<CollectionTypeStats>,
    accountHeader: AccountHeaderUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onCollectionTypeSelected: (CollectionType) -> Unit,
    onAddRecord: () -> Unit,
    onScanRecord: () -> Unit,
    onScanSeries: () -> Unit,
    onAddManga: () -> Unit,
    onAddSeries: () -> Unit,
    onRecordClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onEnterSelection: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {}
) {
    // En mode sélection, le retour système quitte la sélection au lieu de fermer l'écran.
    BackHandler(enabled = selectionMode) { onClearSelection() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeFilterCount = remember(filter) { filter.activeFilterCount() }
    var addSheetVisible by remember { mutableStateOf(false) }
    var serialSheetVisible by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState()
    val serialSheetState = rememberModalBottomSheetState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CollectionDrawerContent(
                activeCollectionType = activeCollectionType,
                visibleCollectionTypes = visibleCollectionTypes,
                stats = collectionTypeStats,
                accountHeader = accountHeader,
                onCollectionTypeSelected = { type ->
                    onCollectionTypeSelected(type)
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
                onOpenAccount = {
                    scope.launch { drawerState.close() }
                    onOpenAccount()
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            floatingActionButton = {
                // Le FAB d'ajout s'efface en mode sélection : les actions passent par la barre contextuelle.
                if (!selectionMode) {
                    FabForState(
                        activeCollectionType = activeCollectionType,
                        listMode = filter.listMode,
                        signedIn = accountHeader.signedIn,
                        onAddRecord = onAddRecord,
                        onAddManga = onAddManga,
                        onAddSeries = onAddSeries,
                        onOpenAddSheet = { addSheetVisible = true },
                        onOpenSerialSheet = { serialSheetVisible = true }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (selectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedIds.size,
                        onClose = onClearSelection,
                        onSelectAll = onSelectAll
                    )
                } else {
                    CollectionSearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClearQuery = onClearQuery,
                        onOpenFilters = onOpenFilters,
                        onOpenSettings = onOpenSettings,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        activeFilterCount = activeFilterCount,
                        onRecordClick = onRecordClick,
                        state = state
                    )
                }
                when (state) {
                    CollectionUiState.Loading -> LoadingState()
                    is CollectionUiState.Empty -> EmptyState(state)
                    is CollectionUiState.FlatContent -> ContentList(
                        state = state,
                        onRecordClick = onRecordClick,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onEnterSelection = onEnterSelection,
                        onToggleSelection = onToggleSelection
                    )
                    is CollectionUiState.SerialContent -> SerialContentList(
                        state = state,
                        onSeriesClick = onSeriesClick,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onEnterSelection = onEnterSelection,
                        onToggleSelection = onToggleSelection
                    )
                }
            }
        }
    }

    if (addSheetVisible) {
        AddSourceBottomSheet(
            sheetState = addSheetState,
            activeCollectionType = activeCollectionType,
            signedIn = accountHeader.signedIn,
            onDismiss = { addSheetVisible = false },
            onManual = {
                scope.launch { addSheetState.hide() }.invokeOnCompletion {
                    addSheetVisible = false
                    when (activeCollectionType) {
                        CollectionType.VINYL -> onAddRecord()
                        CollectionType.MANGA -> onAddManga()
                    }
                }
            },
            onScan = {
                scope.launch { addSheetState.hide() }.invokeOnCompletion {
                    addSheetVisible = false
                    onScanRecord()
                }
            }
        )
    }

    if (serialSheetVisible) {
        SerialAddBottomSheet(
            sheetState = serialSheetState,
            onDismiss = { serialSheetVisible = false },
            onNewSeries = {
                scope.launch { serialSheetState.hide() }.invokeOnCompletion {
                    serialSheetVisible = false
                    onAddSeries()
                }
            },
            onScan = {
                scope.launch { serialSheetState.hide() }.invokeOnCompletion {
                    serialSheetVisible = false
                    onScanSeries()
                }
            }
        )
    }
}

@Composable
private fun FabForState(
    activeCollectionType: CollectionType,
    listMode: ListMode,
    signedIn: Boolean,
    onAddRecord: () -> Unit,
    onAddManga: () -> Unit,
    onAddSeries: () -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenSerialSheet: () -> Unit
) {
    // En vue SERIAL on crée une nouvelle série. En FLAT : connecté, on propose le choix
    // manuel/scan via le bottom sheet ; déconnecté, le scan (API) est indisponible, donc le
    // FAB envoie directement vers la saisie manuelle plutôt qu'un sheet à une seule option.
    val manualAdd = if (activeCollectionType == CollectionType.MANGA) onAddManga else onAddRecord
    val flatAction = if (signedIn) onOpenAddSheet else manualAdd
    // En SERIAL manga + connecté, le scan (import de série) est possible : on propose un sheet
    // « nouvelle série / scanner un tome ». Sinon le FAB crée directement une série vide.
    val serialAction =
        if (activeCollectionType == CollectionType.MANGA && signedIn) onOpenSerialSheet else onAddSeries
    val (labelRes, action) = when {
        listMode == ListMode.SERIAL -> R.string.collection_fab_new_series to serialAction
        activeCollectionType == CollectionType.MANGA -> R.string.collection_fab_add_manga to flatAction
        else -> R.string.collection_fab_add to flatAction
    }
    ExtendedFloatingActionButton(
        onClick = action,
        icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
        text = { Text(stringResource(labelRes)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    activeCollectionType: CollectionType,
    signedIn: Boolean,
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onScan: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.collection_add_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                headlineContent = {
                    Text(stringResource(R.string.collection_add_manual))
                },
                supportingContent = {
                    Text(stringResource(R.string.collection_add_manual_subtitle))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManual)
            )
            // Le scan résout le code-barres via l'API : réservé au mode connecté. Hors session,
            // l'option n'apparaît pas (l'ajout manuel reste disponible hors-ligne).
            if (signedIn) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    },
                    headlineContent = {
                        Text(stringResource(R.string.collection_add_scan))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.collection_add_scan_subtitle))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onScan)
                )
            }
        }
    }
}

/**
 * Feuille d'ajout en **vue séries** (manga connecté) : créer une série vide à la main, ou scanner un
 * tome pour importer la série (cf. [SeriesImportScreen]). N'apparaît que connecté : le scan transite
 * par l'API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SerialAddBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onNewSeries: () -> Unit,
    onScan: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.collection_serial_add_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.collection_serial_scan)) },
                supportingContent = { Text(stringResource(R.string.collection_serial_scan_subtitle)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScan)
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.collection_serial_new_series)) },
                supportingContent = { Text(stringResource(R.string.collection_serial_new_series_subtitle)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewSeries)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
    activeFilterCount: Int,
    onRecordClick: (String) -> Unit,
    state: CollectionUiState
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        windowInsets = WindowInsets(0, 0, 0, 0),
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { expanded = false },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                placeholder = { Text(stringResource(R.string.collection_search_placeholder)) },
                leadingIcon = {
                    if (expanded) {
                        // Quand la SearchBar est étendue, on garde l'icône recherche habituelle ;
                        // le hamburger n'a plus de cible cliquable utile (drawer masqué par la
                        // SearchBar plein écran). On laisse Compose afficher l'icône recherche.
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.collection_open_drawer)
                            )
                        }
                    }
                },
                trailingIcon = {
                    SearchBarTrailing(
                        showClear = query.isNotEmpty(),
                        activeFilterCount = activeFilterCount,
                        onClear = onClearQuery,
                        onOpenFilters = onOpenFilters,
                        onOpenSettings = onOpenSettings
                    )
                }
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        when (state) {
            is CollectionUiState.FlatContent -> ContentList(
                state = state,
                onRecordClick = { id ->
                    expanded = false
                    onRecordClick(id)
                }
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.collection_search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchBarTrailing(
    showClear: Boolean,
    activeFilterCount: Int,
    onClear: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (showClear) {
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.collection_search_clear)
            )
        }
    }
    BadgedBox(
        badge = {
            if (activeFilterCount > 0) {
                Badge { Text(activeFilterCount.toString()) }
            }
        }
    ) {
        IconButton(onClick = onOpenFilters) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(R.string.collection_open_filters)
            )
        }
    }
    IconButton(onClick = onOpenSettings) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.collection_open_settings)
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(state: CollectionUiState.Empty) {
    val (titleRes, subtitleRes, icon) = when {
        state.hasActiveFilters -> Triple(
            R.string.collection_empty_filtered_title,
            R.string.collection_empty_filtered_subtitle,
            Icons.Outlined.Album
        )
        state.listMode == ListMode.SERIAL -> Triple(
            R.string.collection_empty_serial_title,
            R.string.collection_empty_serial_subtitle,
            Icons.Outlined.Book
        )
        state.activeCollectionType == CollectionType.MANGA -> Triple(
            R.string.collection_empty_manga_title,
            R.string.collection_empty_manga_subtitle,
            Icons.Outlined.Book
        )
        else -> Triple(
            R.string.collection_empty_title,
            R.string.collection_empty_subtitle,
            Icons.Outlined.Album
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentList(
    state: CollectionUiState.FlatContent,
    onRecordClick: (String) -> Unit,
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onEnterSelection: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(
                if (state.activeCollectionType == CollectionType.MANGA)
                    R.string.collection_counter_manga
                else
                    R.string.collection_counter,
                state.ownedCount,
                state.wishlistCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            state.sections.forEach { section ->
                stickyHeader(key = "header_${section.letter}") {
                    SectionHeader(section.letter)
                }
                items(
                    items = section.entries,
                    key = { entry -> "${entry.record.id}#${entry.displayedArtist.id}" }
                ) { entry ->
                    val id = entry.record.id
                    val selected = id in selectedIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (selected) Modifier.background(
                                    MaterialTheme.colorScheme.secondaryContainer
                                ) else Modifier
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) onToggleSelection(id) else onRecordClick(id)
                                },
                                onLongClick = { if (!selectionMode) onEnterSelection(id) }
                            )
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { onToggleSelection(id) },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        RecordRow(entry = entry, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SerialContentList(
    state: CollectionUiState.SerialContent,
    onSeriesClick: (String) -> Unit,
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onEnterSelection: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(
                R.string.collection_counter_manga,
                state.ownedCount,
                state.wishlistCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        SeriesGrid(
            items = state.seriesList,
            onOpenSeries = onSeriesClick,
            selectionMode = selectionMode,
            selectedIds = selectedIds,
            onEnterSelection = onEnterSelection,
            onToggleSelection = onToggleSelection
        )
    }
}

/**
 * Barre contextuelle du mode sélection (§2.1) : quitter (croix), compteur, « Tout sélectionner ».
 * Les actions Éditer / Rafraîchir / Supprimer seront ajoutées ici par leurs lots (L3 / L7 / L5).
 */
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.selection_exit)
            )
        }
        Text(
            text = stringResource(R.string.selection_count, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        IconButton(onClick = onSelectAll) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = stringResource(R.string.selection_select_all)
            )
        }
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun RecordFilter.activeFilterCount(): Int {
    var count = 0
    if (status != StatusFilter.ALL) count++
    if (genre != null) count++
    if (format != null) count++
    if (notPlayedSinceSixMonths) count++
    return count
}

@Preview(showBackground = true)
@Composable
private fun CollectionScreenEmptyPreview() {
    AcervatimTheme {
        CollectionScreenContent(
            state = CollectionUiState.Empty(
                activeCollectionType = CollectionType.VINYL,
                listMode = ListMode.FLAT,
                ownedCount = 0,
                wishlistCount = 0,
                hasActiveFilters = false
            ),
            query = "",
            filter = RecordFilter(),
            activeCollectionType = CollectionType.VINYL,
            visibleCollectionTypes = CollectionType.entries.toList(),
            collectionTypeStats = listOf(
                CollectionTypeStats(CollectionType.VINYL, 0, 0),
                CollectionTypeStats(CollectionType.MANGA, 0, 0)
            ),
            accountHeader = AccountHeaderUiState(),
            onQueryChange = {},
            onClearQuery = {},
            onOpenFilters = {},
            onOpenSettings = {},
            onOpenAccount = {},
            onCollectionTypeSelected = {},
            onAddRecord = {},
            onScanRecord = {},
            onScanSeries = {},
            onAddManga = {},
            onAddSeries = {},
            onRecordClick = {},
            onSeriesClick = {}
        )
    }
}
