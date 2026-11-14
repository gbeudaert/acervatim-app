package app.acervatim.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.ImportJobProgress
import app.acervatim.domain.model.ImportPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesImportScreen(
    onBack: () -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    onManual: (barcode: String) -> Unit,
    viewModel: SeriesImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_series_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                !state.hasMatch -> NoMatchContent(
                    onManual = { onManual(state.barcode) }
                )
                state.result != null -> ResultContent(
                    result = state.result!!,
                    onOpenSeries = onOpenSeries
                )
                state.importing -> SeriesImportProgress(
                    title = state.seriesTitle,
                    phase = state.phase,
                    progress = state.progress
                )
                else -> ImportOptionsContent(
                    state = state,
                    onToggleImportAll = viewModel::onToggleImportAll,
                    onImport = viewModel::onImport
                )
            }
        }
    }
}

@Composable
private fun ImportOptionsContent(
    state: SeriesImportUiState,
    onToggleImportAll: (Boolean) -> Unit,
    onImport: () -> Unit
) {
    Text(
        text = stringResource(
            R.string.scan_series_import_detected,
            state.seriesTitle ?: ""
        ),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
    )
    state.scannedVolume?.let { vol ->
        Text(
            text = stringResource(R.string.scan_series_import_volume, vol),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.importing) { onToggleImportAll(!state.importAll) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = state.importAll,
            onCheckedChange = { onToggleImportAll(it) },
            enabled = !state.importing
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.scan_series_import_all),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.scan_series_import_all_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // Pendant l'import, l'écran bascule sur `ImportProgressContent` : le bouton n'a pas d'état de
    // chargement propre (il n'est plus visible dès qu'`importing` passe à vrai).
    Button(
        onClick = onImport,
        enabled = !state.importing,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.scan_series_import_action))
    }
}

/**
 * Avancement de l'import complet (job backend async). Affiche une progression **déterminée** pendant
 * la résolution des jaquettes (`done/total` connu), **indéterminée** sinon (lancement, énumération
 * BnF, écriture locale). Cf. [ScanImportPhase].
 *
 * Partagé avec la recherche par titre ([SeriesSearchScreen]) : même job, mêmes phases — deux écrans
 * d'avancement divergeraient au premier changement de libellé.
 */
@Composable
internal fun SeriesImportProgress(
    title: String?,
    phase: ScanImportPhase,
    progress: ImportJobProgress?
) {
    val label = when {
        phase == ScanImportPhase.IMPORTING ->
            stringResource(R.string.scan_series_import_finalizing)
        progress?.phase == ImportPhase.COVERS ->
            stringResource(R.string.scan_series_import_phase_covers)
        phase == ScanImportPhase.POLLING ->
            stringResource(R.string.scan_series_import_phase_enumerating)
        else -> stringResource(R.string.scan_series_import_launching)
    }

    Text(
        text = title ?: "",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val showDeterminate = progress?.phase == ImportPhase.COVERS && progress.total > 0
    if (showDeterminate) {
        LinearProgressIndicator(
            progress = { progress.done.toFloat() / progress.total.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(
                R.string.scan_series_import_phase_covers_count,
                progress.done,
                progress.total
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ResultContent(
    result: app.acervatim.domain.usecase.ImportResult,
    onOpenSeries: (String) -> Unit
) {
    Text(
        text = stringResource(
            R.string.scan_series_import_result,
            result.createdVolumes,
            result.skippedExisting
        ),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    when {
        result.degradedFullImport -> WarningText(stringResource(R.string.scan_series_import_unavailable))
        result.partial -> WarningText(stringResource(R.string.scan_series_import_partial))
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onOpenSeries(result.seriesId) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.scan_series_import_open))
    }
}

@Composable
private fun NoMatchContent(onManual: () -> Unit) {
    Text(
        text = stringResource(R.string.scan_series_import_no_match),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = onManual,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.scan_series_import_manual))
    }
}

@Composable
private fun WarningText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
