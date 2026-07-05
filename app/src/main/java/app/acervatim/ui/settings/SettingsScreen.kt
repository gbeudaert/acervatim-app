package app.acervatim.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.ui.theme.AcervatimTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onManageCollectionTypes: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val duplicate by viewModel.duplicateAlbumsPerArtist.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    SettingsContent(
        duplicateAlbumsPerArtist = duplicate,
        onDuplicateAlbumsToggle = viewModel::onDuplicateAlbumsToggle,
        backupState = backupState,
        onExportTo = viewModel::onExportTo,
        onImportFrom = viewModel::onImportFrom,
        onConsumeBackupMessage = viewModel::consumeBackupMessage,
        onOpenAccount = onOpenAccount,
        onManageCollectionTypes = onManageCollectionTypes,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    duplicateAlbumsPerArtist: Boolean,
    onDuplicateAlbumsToggle: (Boolean) -> Unit,
    backupState: BackupOpState,
    onExportTo: (Uri) -> Unit,
    onImportFrom: (Uri) -> Unit,
    onConsumeBackupMessage: () -> Unit,
    onOpenAccount: () -> Unit,
    onManageCollectionTypes: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) onExportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) pendingImportUri = uri
    }

    // Snackbar feedback : success / error
    val exportSuccessFormat = stringResource(R.string.backup_export_success)
    val exportErrorFormat = stringResource(R.string.backup_export_error)
    val importSuccessFormat = stringResource(R.string.backup_import_success)
    val importErrorFormat = stringResource(R.string.backup_import_error)
    LaunchedEffect(backupState) {
        val message = when (val s = backupState) {
            is BackupOpState.ExportDone -> exportSuccessFormat.format(s.recordCount, s.seriesCount)
            is BackupOpState.ImportDone -> importSuccessFormat.format(s.recordCount, s.seriesCount)
            is BackupOpState.Error -> {
                // On ne sait pas si c'est un export ou un import qui a échoué — on prend le label générique d'import
                // (les deux strings ont la même structure "Échec : %1$s").
                importErrorFormat.format(s.message)
            }
            else -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumeBackupMessage()
        }
    }

    val defaultBackupNameFormat = stringResource(R.string.backup_default_filename)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            SectionLabel(stringResource(R.string.account_title))
            ActionRow(
                icon = Icons.Filled.CloudSync,
                title = stringResource(R.string.account_open),
                subtitle = stringResource(R.string.account_signed_out_subtitle),
                enabled = true,
                showSpinner = false,
                onClick = onOpenAccount
            )
            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_section_collection))
            ActionRow(
                icon = Icons.Filled.Category,
                title = stringResource(R.string.settings_manage_types_title),
                subtitle = stringResource(R.string.settings_manage_types_subtitle),
                enabled = true,
                showSpinner = false,
                onClick = onManageCollectionTypes
            )
            ToggleRow(
                title = stringResource(R.string.settings_duplicate_albums_title),
                description = stringResource(R.string.settings_duplicate_albums_description),
                checked = duplicateAlbumsPerArtist,
                onCheckedChange = onDuplicateAlbumsToggle
            )
            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_section_data))
            Text(
                text = stringResource(R.string.settings_section_data_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val busy = backupState is BackupOpState.Busy
            ActionRow(
                icon = Icons.Filled.FileDownload,
                title = stringResource(R.string.settings_data_export),
                subtitle = stringResource(R.string.settings_data_export_subtitle),
                enabled = !busy,
                showSpinner = busy,
                onClick = {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    exportLauncher.launch(defaultBackupNameFormat.format(today))
                }
            )
            ActionRow(
                icon = Icons.Filled.FileUpload,
                title = stringResource(R.string.settings_data_import),
                subtitle = stringResource(R.string.settings_data_import_subtitle),
                enabled = !busy,
                showSpinner = busy,
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            )
            HorizontalDivider()
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    onImportFrom(uri)
                }) {
                    Text(stringResource(R.string.backup_import_confirm_cta))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.backup_import_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    showSpinner: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    AcervatimTheme {
        SettingsContent(
            duplicateAlbumsPerArtist = true,
            onDuplicateAlbumsToggle = {},
            backupState = BackupOpState.Idle,
            onExportTo = {},
            onImportFrom = {},
            onConsumeBackupMessage = {},
            onOpenAccount = {},
            onManageCollectionTypes = {},
            onBack = {}
        )
    }
}
