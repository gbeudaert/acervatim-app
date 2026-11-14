package app.acervatim.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.EditionTome
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.usecase.BulkEditResult
import app.acervatim.domain.usecase.DeleteResult
import app.acervatim.domain.usecase.ImportResult
import app.acervatim.domain.usecase.RefreshResult
import app.acervatim.ui.components.ZoomableCover
import app.acervatim.ui.list.BulkEditSheet
import app.acervatim.ui.list.RefreshOptionDialog

@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenVolume: (String) -> Unit,
    onAddVolume: (seriesId: String, suggestedVolume: Int?) -> Unit,
    onScanVolume: (seriesId: String) -> Unit,
    onEditSeries: (String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val bulkEditSheetOpen by viewModel.bulkEditSheetOpen.collectAsStateWithLifecycle()
    val bulkEditResult by viewModel.bulkEditResult.collectAsStateWithLifecycle()
    val bulkDeleteResult by viewModel.bulkDeleteResult.collectAsStateWithLifecycle()
    val refreshResult by viewModel.refreshResult.collectAsStateWithLifecycle()
    val newVolumes by viewModel.newVolumes.collectAsStateWithLifecycle()
    val addingNewVolumes by viewModel.addingNewVolumes.collectAsStateWithLifecycle()
    val newVolumesResult by viewModel.newVolumesResult.collectAsStateWithLifecycle()
    val newVolumesFailed by viewModel.newVolumesFailed.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        if (event is SeriesDetailEvent.Deleted) {
            viewModel.onEventConsumed()
            onBack()
        }
    }

    SeriesDetailContent(
        state = state,
        onBack = onBack,
        onOpenVolume = onOpenVolume,
        onAddVolume = onAddVolume,
        onScanVolume = onScanVolume,
        onEditSeries = onEditSeries,
        onDelete = viewModel::onDelete,
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onEnterSelection = viewModel::enterSelection,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAllVisible,
        onClearSelection = viewModel::clearSelection,
        onEditSelection = viewModel::openBulkEdit,
        bulkEditResult = bulkEditResult,
        onBulkEditResultShown = viewModel::consumeBulkEditResult,
        onConfirmBulkDelete = viewModel::applyBulkDelete,
        bulkDeleteResult = bulkDeleteResult,
        onBulkDeleteResultShown = viewModel::consumeBulkDeleteResult,
        onRefresh = viewModel::applyRefresh,
        refreshResult = refreshResult,
        onRefreshResultShown = viewModel::consumeRefreshResult,
        newVolumes = newVolumes,
        addingNewVolumes = addingNewVolumes,
        onAddNewVolumes = viewModel::onAddNewVolumes,
        onDismissNewVolumes = viewModel::onDismissNewVolumes,
        newVolumesResult = newVolumesResult,
        onNewVolumesResultShown = viewModel::consumeNewVolumesResult,
        newVolumesFailed = newVolumesFailed,
        onNewVolumesFailedShown = viewModel::consumeNewVolumesFailed
    )

    if (bulkEditSheetOpen && state is SeriesDetailUiState.Loaded) {
        BulkEditSheet(
            collectionType = (state as SeriesDetailUiState.Loaded).series.collectionType,
            onApply = viewModel::applyBulkEdit,
            onDismiss = viewModel::closeBulkEdit
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesDetailContent(
    state: SeriesDetailUiState,
    onBack: () -> Unit,
    onOpenVolume: (String) -> Unit,
    onAddVolume: (seriesId: String, suggestedVolume: Int?) -> Unit,
    onEditSeries: (String) -> Unit,
    onScanVolume: (seriesId: String) -> Unit = {},
    onDelete: (cascade: Boolean) -> Unit = {},
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onEnterSelection: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onEditSelection: () -> Unit = {},
    bulkEditResult: BulkEditResult? = null,
    onBulkEditResultShown: () -> Unit = {},
    onConfirmBulkDelete: () -> Unit = {},
    bulkDeleteResult: DeleteResult? = null,
    onBulkDeleteResultShown: () -> Unit = {},
    onRefresh: (overwrite: Boolean) -> Unit = {},
    refreshResult: RefreshResult? = null,
    onRefreshResultShown: () -> Unit = {},
    newVolumes: List<EditionTome> = emptyList(),
    addingNewVolumes: Boolean = false,
    onAddNewVolumes: () -> Unit = {},
    onDismissNewVolumes: () -> Unit = {},
    newVolumesResult: ImportResult? = null,
    onNewVolumesResultShown: () -> Unit = {},
    newVolumesFailed: Boolean = false,
    onNewVolumesFailedShown: () -> Unit = {}
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmBulkDelete by rememberSaveable { mutableStateOf(false) }
    var showRefreshOptions by rememberSaveable { mutableStateOf(false) }

    // En mode sélection, le retour système quitte la sélection au lieu de fermer l'écran.
    BackHandler(enabled = selectionMode) { onClearSelection() }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(bulkEditResult) {
        val result = bulkEditResult ?: return@LaunchedEffect
        val message = if (result.hasFailures) {
            context.getString(R.string.bulk_edit_result_partial, result.updatedCount, result.failedCount)
        } else {
            context.getString(R.string.bulk_edit_result, result.updatedCount)
        }
        snackbarHostState.showSnackbar(message)
        onBulkEditResultShown()
    }
    LaunchedEffect(bulkDeleteResult) {
        val result = bulkDeleteResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(R.string.bulk_delete_result, result.total))
        onBulkDeleteResultShown()
    }
    LaunchedEffect(refreshResult) {
        val result = refreshResult ?: return@LaunchedEffect
        val message = if (result.hasFailures) {
            context.getString(
                R.string.refresh_result_partial,
                result.refreshedCount, result.unchangedCount, result.failedCount
            )
        } else {
            context.getString(R.string.refresh_result, result.refreshedCount, result.unchangedCount)
        }
        snackbarHostState.showSnackbar(message)
        onRefreshResultShown()
    }
    LaunchedEffect(newVolumesResult) {
        val result = newVolumesResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.resources.getQuantityString(
                R.plurals.series_catch_up_result, result.createdVolumes, result.createdVolumes
            )
        )
        onNewVolumesResultShown()
    }
    LaunchedEffect(newVolumesFailed) {
        if (!newVolumesFailed) return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(R.string.series_catch_up_failed))
        onNewVolumesFailedShown()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SeriesSelectionTopBar(
                    selectedCount = selectedIds.size,
                    onClose = onClearSelection,
                    onEdit = onEditSelection,
                    onRefresh = { showRefreshOptions = true },
                    onDelete = { confirmBulkDelete = true },
                    onSelectAll = onSelectAll
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.series_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        // Actions sur la série chargée : scan d'un tome, édition, suppression.
                        // Indisponibles tant qu'on n'a pas l'id (état Loading/NotFound).
                        if (state is SeriesDetailUiState.Loaded) {
                            IconButton(onClick = { onScanVolume(state.series.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = stringResource(R.string.series_detail_scan)
                                )
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.action_more)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_edit)) },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onEditSeries(state.series.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_delete)) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            confirmDelete = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Le FAB d'ajout s'efface en mode sélection.
            if (!selectionMode && state is SeriesDetailUiState.Loaded) {
                val maxVolume = state.volumes.mapNotNull { it.volumeNumber }.maxOrNull()
                val suggested = maxVolume?.plus(1) ?: 1
                ExtendedFloatingActionButton(
                    onClick = { onAddVolume(state.series.id, suggested) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.series_detail_add_volume)) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                SeriesDetailUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                SeriesDetailUiState.NotFound -> Text(
                    text = stringResource(R.string.series_detail_not_found),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is SeriesDetailUiState.Loaded -> SeriesDetailBody(
                    series = state.series,
                    volumes = state.volumes,
                    onOpenVolume = onOpenVolume,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onEnterSelection = onEnterSelection,
                    onToggleSelection = onToggleSelection,
                    // Masquée en mode sélection : la barre d'actions occupe alors l'attention.
                    banner = if (newVolumes.isNotEmpty() && !selectionMode) {
                        {
                            NewVolumesBanner(
                                tomes = newVolumes,
                                adding = addingNewVolumes,
                                onAdd = onAddNewVolumes,
                                onLater = onDismissNewVolumes
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }

    if (showRefreshOptions) {
        RefreshOptionDialog(
            onConfirm = { overwrite ->
                showRefreshOptions = false
                onRefresh(overwrite)
            },
            onDismiss = { showRefreshOptions = false }
        )
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(stringResource(R.string.bulk_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.bulk_delete_confirm_entries, selectedIds.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkDelete = false
                    onConfirmBulkDelete()
                }) {
                    Text(
                        text = stringResource(R.string.bulk_delete_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (confirmDelete && state is SeriesDetailUiState.Loaded) {
        SeriesDeleteDialog(
            tomeCount = state.volumes.size,
            onCascade = {
                confirmDelete = false
                onDelete(true)
            },
            onOrphan = {
                confirmDelete = false
                onDelete(false)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * Barre contextuelle du mode sélection des tomes (§2.1) : quitter, compteur, Éditer, Tout sélectionner.
 * L'action Supprimer (des tomes) sera ajoutée par le lot L5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesSelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selection_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.selection_exit)
                )
            }
        },
        actions = {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.selection_edit)
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.selection_refresh)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.selection_delete)
                )
            }
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = stringResource(R.string.selection_select_all)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    )
}

/**
 * Dialog de suppression d'une série. Quand la série contient des tomes, propose explicitement les deux
 * modes (cf. `spec-edition-multiple.md` §2.4) : **Cascade** (série + tomes) ou **Orphelins** (nœud seul,
 * tomes conservés). Sans tome, un simple bouton Supprimer suffit.
 */
@Composable
private fun SeriesDeleteDialog(
    tomeCount: Int,
    onCascade: () -> Unit,
    onOrphan: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.series_delete_title)) },
        text = {
            Text(
                if (tomeCount == 0) stringResource(R.string.series_delete_message)
                else pluralStringResource(R.plurals.series_delete_prompt, tomeCount, tomeCount)
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (tomeCount > 0) {
                    TextButton(onClick = onCascade) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.series_delete_cascade, tomeCount, tomeCount
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = onOrphan) {
                        Text(stringResource(R.string.series_delete_orphan))
                    }
                } else {
                    // Aucun tome : cascade et orphelins sont équivalents, un seul choix.
                    TextButton(onClick = onOrphan) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
