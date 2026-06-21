package app.acervatim.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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

    CollectionScreenContent(
        state = state,
        query = query,
        filter = filter,
        activeCollectionType = activeType,
        visibleCollectionTypes = visibleTypes,
        collectionTypeStats = typeStats,
        accountHeader = accountHeader,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onOpenFilters = viewModel::openFilterSheet,
        onOpenSettings = onOpenSettings,
        onOpenAccount = onOpenAccount,
        onCollectionTypeSelected = viewModel::onCollectionTypeSelected,
        onAddRecord = onAddRecord,
        onScanRecord = { onScanRecord(activeType) },
        onAddManga = onAddManga,
        onAddSeries = { onAddSeries(activeType) },
        onRecordClick = onOpenRecord,
        onSeriesClick = onOpenSeries
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
    onAddManga: () -> Unit,
    onAddSeries: () -> Unit,
    onRecordClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeFilterCount = remember(filter) { filter.activeFilterCount() }
    var addSheetVisible by remember { mutableStateOf(false) }
    val addSheetState = rememberModalBottomSheetState()

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
                FabForState(
                    activeCollectionType = activeCollectionType,
                    listMode = filter.listMode,
                    signedIn = accountHeader.signedIn,
                    onAddRecord = onAddRecord,
                    onAddManga = onAddManga,
                    onAddSeries = onAddSeries,
                    onOpenAddSheet = { addSheetVisible = true }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
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
                when (state) {
                    CollectionUiState.Loading -> LoadingState()
                    is CollectionUiState.Empty -> EmptyState(state)
                    is CollectionUiState.FlatContent -> ContentList(
                        state = state,
                        onRecordClick = onRecordClick
                    )
                    is CollectionUiState.SerialContent -> SerialContentList(
                        state = state,
                        onSeriesClick = onSeriesClick
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
}

@Composable
private fun FabForState(
    activeCollectionType: CollectionType,
    listMode: ListMode,
    signedIn: Boolean,
    onAddRecord: () -> Unit,
    onAddManga: () -> Unit,
    onAddSeries: () -> Unit,
    onOpenAddSheet: () -> Unit
) {
    // En vue SERIAL on crée une nouvelle série. En FLAT : connecté, on propose le choix
    // manuel/scan via le bottom sheet ; déconnecté, le scan (API) est indisponible, donc le
    // FAB envoie directement vers la saisie manuelle plutôt qu'un sheet à une seule option.
    val manualAdd = if (activeCollectionType == CollectionType.MANGA) onAddManga else onAddRecord
    val flatAction = if (signedIn) onOpenAddSheet else manualAdd
    val (labelRes, action) = when {
        listMode == ListMode.SERIAL -> R.string.collection_fab_new_series to onAddSeries
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
    onRecordClick: (String) -> Unit
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
                    RecordRow(
                        entry = entry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecordClick(entry.record.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SerialContentList(
    state: CollectionUiState.SerialContent,
    onSeriesClick: (String) -> Unit
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
            onOpenSeries = onSeriesClick
        )
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
            onAddManga = {},
            onAddSeries = {},
            onRecordClick = {},
            onSeriesClick = {}
        )
    }
}
