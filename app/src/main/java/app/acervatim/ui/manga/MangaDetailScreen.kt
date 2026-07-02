package app.acervatim.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.readString
import app.acervatim.ui.components.ZoomableCover

@Composable
fun MangaDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    viewModel: MangaDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        if (event is MangaDetailEvent.Deleted) {
            viewModel.onEventConsumed()
            onBack()
        }
    }

    MangaDetailContent(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onWishlistToggle = viewModel::onWishlistToggle,
        onRatingChange = viewModel::onRatingChange,
        onDelete = viewModel::onDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaDetailContent(
    state: MangaDetailUiState,
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    onWishlistToggle: (Boolean) -> Unit,
    onRatingChange: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (state is MangaDetailUiState.Loaded) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.action_more)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_edit)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit(state.record.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                MangaDetailUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                MangaDetailUiState.NotFound -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.manga_detail_not_found),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MangaDetailUiState.Loaded -> LoadedBody(
                    record = state.record,
                    series = state.series,
                    onWishlistToggle = onWishlistToggle,
                    onRatingChange = onRatingChange
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.manga_detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun LoadedBody(
    record: Record,
    series: Series?,
    onWishlistToggle: (Boolean) -> Unit,
    onRatingChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CoverHeader(record = record)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            record.primaryArtist?.name?.takeIf { it.isNotBlank() }?.let { author ->
                Text(text = author, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InfoChips(record = record)

        // Correspondance inter-édition : « Contient les tomes 1 à 3 » (454$h).
        MangaExtras.sourceVolumeLabel(record.extras.readString(MangaExtras.KEY_SOURCE_VOLUME_RANGE))
            ?.let { label ->
                Text(
                    text = stringResource(R.string.manga_detail_contains, label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

        StarRating(rating = record.rating ?: 0, onRatingChange = onRatingChange)

        // Résumé : les sources n'exposent pas de synopsis par tome → on montre celui de la série.
        series?.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.manga_detail_synopsis),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()
        StatusSection(status = record.status, onWishlistToggle = onWishlistToggle)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CoverHeader(record: Record) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ZoomableCover(
            coverPath = record.coverPath,
            contentDescription = record.title,
            cornerRadius = 8.dp,
            cacheKey = record.id,
            placeholderIconSize = 96.dp,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(0.7f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoChips(record: Record) {
    val volumeLabel = record.volumeNumber?.let { stringResource(R.string.series_detail_volume_label, it) }
    val edition = record.extras.readString(MangaExtras.KEY_EDITION)
    val publisher = record.extras.readString(MangaExtras.KEY_PUBLISHER_FR)
    val chips = buildList {
        volumeLabel?.let { add(it) }
        edition?.let { add(it) }
        publisher?.let { add(it) }
        record.isbn?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    if (chips.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { value ->
            AssistChip(onClick = {}, label = { Text(value) })
        }
    }
}

@Composable
private fun StarRating(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val starIndex = index + 1
            val filled = starIndex <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = stringResource(R.string.detail_rating_star, starIndex),
                tint = if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onRatingChange(if (rating == starIndex) 0 else starIndex) }
            )
        }
    }
}

@Composable
private fun StatusSection(status: ItemStatus, onWishlistToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = stringResource(R.string.detail_status_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.add_record_wishlist_toggle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = status == ItemStatus.WISHLIST,
            onCheckedChange = onWishlistToggle
        )
    }
}
