package app.acervatim.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.Source
import app.acervatim.ui.theme.AcervatimTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ManageCollectionTypesScreen(
    onBack: () -> Unit,
    viewModel: ManageCollectionTypesViewModel = hiltViewModel()
) {
    val items by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val connectErrorText = stringResource(R.string.sources_error_connect)

    // Retour au premier plan (après le passage dans le navigateur pour l'OAuth) ⇒ on relit l'état.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshConnectedProviders()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SourcesEvent.OpenUrl -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                        .onFailure { snackbarHostState.showSnackbar(connectErrorText) }
                }
                SourcesEvent.ConnectError -> snackbarHostState.showSnackbar(connectErrorText)
            }
        }
    }

    ManageCollectionTypesContent(
        items = items,
        snackbarHostState = snackbarHostState,
        onVisibilityChange = viewModel::onVisibilityChange,
        onSyncedChange = viewModel::onSyncedChange,
        onListModeChange = viewModel::onListModeChange,
        onConnectSource = viewModel::onConnectSource,
        onDisconnectSource = viewModel::onDisconnectSource,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageCollectionTypesContent(
    items: List<CollectionTypeManageUiState>,
    snackbarHostState: SnackbarHostState,
    onVisibilityChange: (CollectionType, Boolean) -> Unit,
    onSyncedChange: (CollectionType, Boolean) -> Unit,
    onListModeChange: (CollectionType, ListMode) -> Unit,
    onConnectSource: (String) -> Unit,
    onDisconnectSource: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sourcesForType by remember { mutableStateOf<CollectionType?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_types_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.manage_types_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            items.forEach { item ->
                CollectionTypeBlock(
                    item = item,
                    onVisibilityChange = { visible -> onVisibilityChange(item.type, visible) },
                    onSyncedChange = { synced -> onSyncedChange(item.type, synced) },
                    onListModeChange = { mode -> onListModeChange(item.type, mode) },
                    onOpenSources = { sourcesForType = item.type }
                )
                HorizontalDivider()
            }
        }
    }

    val sheetItem = sourcesForType?.let { type -> items.firstOrNull { it.type == type } }
    if (sheetItem != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { sourcesForType = null },
            sheetState = sheetState
        ) {
            SourcesSheetContent(
                item = sheetItem,
                onConnect = onConnectSource,
                onDisconnect = onDisconnectSource
            )
        }
    }
}

@Composable
private fun CollectionTypeBlock(
    item: CollectionTypeManageUiState,
    onVisibilityChange: (Boolean) -> Unit,
    onSyncedChange: (Boolean) -> Unit,
    onListModeChange: (ListMode) -> Unit,
    onOpenSources: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = item.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.type.labelRes()),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.manage_types_item_count, item.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SwitchRow(
            label = stringResource(R.string.manage_types_visible_label),
            checked = item.visible,
            enabled = item.visibilityToggleEnabled,
            onCheckedChange = onVisibilityChange
        )
        SwitchRow(
            label = stringResource(R.string.manage_types_synced_label),
            checked = item.synced,
            enabled = true,
            onCheckedChange = onSyncedChange
        )

        if (item.sources.isNotEmpty()) {
            SourcesStatusRow(item = item, onClick = onOpenSources)
        }

        if (item.supportsSerial) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.manage_types_display_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                ListModeSelector(selected = item.listMode, onChange = onListModeChange)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SourcesStatusRow(
    item: CollectionTypeManageUiState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val status = if (item.hasTokenSources) {
            stringResource(
                R.string.manage_types_sources_connected,
                item.connectedTokenCount,
                item.tokenSources.size
            )
        } else {
            stringResource(R.string.manage_types_sources_all_public)
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.manage_types_sources_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListModeSelector(
    selected: ListMode,
    onChange: (ListMode) -> Unit
) {
    val options = remember {
        listOf(
            ListMode.FLAT to R.string.filters_list_mode_flat,
            ListMode.SERIAL to R.string.filters_list_mode_serial
        )
    }
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, (mode, labelRes) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { if (selected != mode) onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun SourcesSheetContent(
    item: CollectionTypeManageUiState,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.sources_sheet_title, stringResource(item.type.labelRes())),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        item.sources.forEach { source ->
            SourceRow(source = source, onConnect = onConnect, onDisconnect = onDisconnect)
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceUiState,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(source.source.labelRes()),
                style = MaterialTheme.typography.bodyLarge
            )
            if (!source.requiresToken) {
                Text(
                    text = stringResource(R.string.sources_public_no_token),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (source.connected) {
                Text(
                    text = stringResource(R.string.sources_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (source.requiresToken) {
            if (source.connected) {
                TextButton(onClick = { onDisconnect(source.source.providerCode) }) {
                    Text(stringResource(R.string.sources_action_disconnect))
                }
            } else {
                TextButton(onClick = { onConnect(source.source.providerCode) }) {
                    Text(stringResource(R.string.sources_action_connect))
                }
            }
        }
    }
}

private fun CollectionType.labelRes(): Int = when (this) {
    CollectionType.VINYL -> R.string.collection_type_vinyl
    CollectionType.MANGA -> R.string.collection_type_manga
}

private fun CollectionType.icon(): ImageVector = when (this) {
    CollectionType.VINYL -> Icons.Filled.Album
    CollectionType.MANGA -> Icons.Filled.Book
}

private fun Source.labelRes(): Int = when (this) {
    Source.DISCOGS -> R.string.source_discogs
    Source.MYANIMELIST -> R.string.source_myanimelist
    Source.BNF -> R.string.source_bnf
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun ManageCollectionTypesPreview() {
    AcervatimTheme {
        ManageCollectionTypesContent(
            items = listOf(
                CollectionTypeManageUiState(
                    type = CollectionType.VINYL,
                    visible = true,
                    visibilityToggleEnabled = true,
                    synced = true,
                    supportsSerial = false,
                    listMode = ListMode.FLAT,
                    itemCount = 342,
                    sources = listOf(SourceUiState(Source.DISCOGS, connected = true))
                ),
                CollectionTypeManageUiState(
                    type = CollectionType.MANGA,
                    visible = true,
                    visibilityToggleEnabled = true,
                    synced = false,
                    supportsSerial = true,
                    listMode = ListMode.SERIAL,
                    itemCount = 128,
                    sources = listOf(
                        SourceUiState(Source.MYANIMELIST, connected = false),
                        SourceUiState(Source.BNF, connected = false)
                    )
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onVisibilityChange = { _, _ -> },
            onSyncedChange = { _, _ -> },
            onListModeChange = { _, _ -> },
            onConnectSource = {},
            onDisconnectSource = {},
            onBack = {}
        )
    }
}
