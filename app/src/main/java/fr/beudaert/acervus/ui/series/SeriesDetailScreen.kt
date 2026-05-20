package fr.beudaert.acervus.ui.series

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.beudaert.acervus.R
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.ui.components.RecordCover

@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenVolume: (String) -> Unit,
    onAddVolume: (seriesId: String, suggestedVolume: Int?) -> Unit,
    onEditSeries: (String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SeriesDetailContent(
        state = state,
        onBack = onBack,
        onOpenVolume = onOpenVolume,
        onAddVolume = onAddVolume,
        onEditSeries = onEditSeries,
        onSetStatus = viewModel::onSetStatus
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesDetailContent(
    state: SeriesDetailUiState,
    onBack: () -> Unit,
    onOpenVolume: (String) -> Unit,
    onAddVolume: (seriesId: String, suggestedVolume: Int?) -> Unit,
    onEditSeries: (String) -> Unit,
    onSetStatus: (String, fr.beudaert.acervus.domain.model.ItemStatus) -> Unit
) {
    Scaffold(
        topBar = {
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
                    // Édition de la série (jaquette, total tomes, synopsis, …). N'apparaît qu'une
                    // fois la série chargée — sinon on n'a pas d'id à passer à AddEditSeriesScreen.
                    if (state is SeriesDetailUiState.Loaded) {
                        IconButton(onClick = { onEditSeries(state.series.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state is SeriesDetailUiState.Loaded) {
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
                is SeriesDetailUiState.Loaded -> LoadedContent(
                    series = state.series,
                    volumes = state.volumes,
                    onOpenVolume = onOpenVolume,
                    onSetStatus = onSetStatus
                )
            }
        }
    }
}

@Composable
private fun LoadedContent(
    series: Series,
    volumes: List<Record>,
    onOpenVolume: (String) -> Unit,
    onSetStatus: (String, fr.beudaert.acervus.domain.model.ItemStatus) -> Unit
) {
    var headerExpanded by rememberSaveable { mutableStateOf(true) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesHeader(
                series = series,
                expanded = headerExpanded,
                onToggle = { headerExpanded = !headerExpanded }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.series_detail_volumes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }
        items(items = volumes, key = { it.id }) { record ->
            VolumeTile(
                record = record,
                onClick = { onOpenVolume(record.id) },
                onSetStatus = { status -> onSetStatus(record.id, status) }
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    series: Series,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecordCover(
                coverPath = series.coverPath,
                contentDescription = series.title,
                cornerRadius = 8.dp,
                cacheKey = "series-${series.id}",
                modifier = Modifier
                    .height(128.dp)
                    .aspectRatio(0.75f)
            )
            Spacer(Modifier.width(20.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (series.originalAuthor != null) {
                    Text(
                        text = series.originalAuthor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (series.totalVolumes != null) {
                    Text(
                        text = stringResource(R.string.series_detail_total_volumes, series.totalVolumes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val toggleSemantics = stringResource(
                if (expanded) R.string.series_detail_collapse else R.string.series_detail_expand
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = toggleSemantics
                )
            }
        }
        if (expanded && !series.synopsis.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            val scrollState = rememberScrollState()
            Text(
                text = series.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .verticalScroll(scrollState)
            )
        }
    }
}
