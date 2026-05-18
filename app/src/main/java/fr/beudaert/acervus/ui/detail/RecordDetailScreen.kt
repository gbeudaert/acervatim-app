package fr.beudaert.acervus.ui.detail

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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.beudaert.acervus.R
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.RecordStatus
import fr.beudaert.acervus.domain.model.RecordingSpeed
import fr.beudaert.acervus.ui.components.RecordCover
import fr.beudaert.acervus.ui.theme.AcervusTheme
import fr.beudaert.acervus.ui.util.formatRelativeTime
import java.time.Clock
import java.time.Instant

@Composable
fun RecordDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: RecordDetailViewModel = hiltViewModel(),
    clock: Clock = Clock.systemDefaultZone()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val now = remember(clock) { clock.instant() }

    LaunchedEffect(event) {
        if (event is RecordDetailEvent.Deleted) {
            viewModel.onEventConsumed()
            onBack()
        }
    }

    RecordDetailContent(
        state = state,
        now = now,
        onBack = onBack,
        onEdit = onEdit,
        onMarkAsPlayed = viewModel::onMarkAsPlayed,
        onResetLastPlayed = viewModel::onResetLastPlayed,
        onWishlistToggle = viewModel::onWishlistToggle,
        onRatingChange = viewModel::onRatingChange,
        onDelete = viewModel::onDelete
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordDetailContent(
    state: RecordDetailUiState,
    now: Instant,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onMarkAsPlayed: () -> Unit,
    onResetLastPlayed: () -> Unit,
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
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (state is RecordDetailUiState.Loaded) {
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
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit(state.record.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    },
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
                RecordDetailUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                RecordDetailUiState.NotFound -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.detail_not_found),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is RecordDetailUiState.Loaded -> LoadedBody(
                    record = state.record,
                    now = now,
                    onMarkAsPlayed = onMarkAsPlayed,
                    onResetLastPlayed = onResetLastPlayed,
                    onWishlistToggle = onWishlistToggle,
                    onRatingChange = onRatingChange
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadedBody(
    record: Record,
    now: Instant,
    onMarkAsPlayed: () -> Unit,
    onResetLastPlayed: () -> Unit,
    onWishlistToggle: (Boolean) -> Unit,
    onRatingChange: (Int) -> Unit
) {
    val primaryArtistName = record.primaryArtist?.name.orEmpty()
    val featuringNames = record.featuringArtists.joinToString(", ") { it.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CoverHeader(record = record)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = primaryArtistName,
                style = MaterialTheme.typography.headlineSmall
            )
            if (featuringNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.detail_featuring, featuringNames),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        StarRating(rating = record.rating ?: 0, onRatingChange = onRatingChange)

        InfoChips(record = record)

        HorizontalDivider()
        LastPlayedSection(
            lastPlayedAt = record.lastPlayedAt,
            now = now,
            onMarkAsPlayed = onMarkAsPlayed,
            onResetLastPlayed = onResetLastPlayed
        )

        HorizontalDivider()
        StatusSection(
            status = record.status,
            onWishlistToggle = onWishlistToggle
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CoverHeader(record: Record) {
    val description = "${record.title} – ${record.primaryArtist?.name.orEmpty()}"
    RecordCover(
        coverPath = record.coverPath,
        contentDescription = description,
        cornerRadius = 8.dp,
        cacheKey = record.id,
        placeholderIconSize = 96.dp,
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .aspectRatio(1f)
    )
}

@Composable
private fun StarRating(
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
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
                    .clickable {
                        onRatingChange(if (rating == starIndex) 0 else starIndex)
                    }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoChips(record: Record) {
    val formatLabel = record.format?.label()
    val speedLabel = record.recordingSpeed?.label()
    val chips = remember(record, formatLabel, speedLabel) {
        buildList {
            record.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
            formatLabel?.let { add(it) }
            speedLabel?.let { add(it) }
            record.year?.let { add(it.toString()) }
            record.label?.takeIf { it.isNotBlank() }?.let { add(it) }
            record.country?.takeIf { it.isNotBlank() }?.let { add(it) }
            record.purchasePrice?.let { add("%.2f €".format(it)) }
        }
    }
    if (chips.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { value ->
            AssistChip(
                onClick = {},
                label = { Text(value) }
            )
        }
    }
}

@Composable
private fun LastPlayedSection(
    lastPlayedAt: Instant?,
    now: Instant,
    onMarkAsPlayed: () -> Unit,
    onResetLastPlayed: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.detail_last_played_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        val text = if (lastPlayedAt == null) {
            stringResource(R.string.detail_last_played_never)
        } else {
            formatRelativeTime(lastPlayedAt, now)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (lastPlayedAt == null) FontWeight.Normal else FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMarkAsPlayed) {
                Text(stringResource(R.string.detail_action_played_now))
            }
            if (lastPlayedAt != null) {
                TextButton(onClick = onResetLastPlayed) {
                    Text(
                        text = stringResource(R.string.detail_action_reset_played),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    status: RecordStatus,
    onWishlistToggle: (Boolean) -> Unit
) {
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
            checked = status.isWishlist(),
            onCheckedChange = onWishlistToggle
        )
    }
}

@Composable
private fun AcervusFormat.label(): String = when (this) {
    AcervusFormat.LP_33 -> stringResource(R.string.format_lp_33)
    AcervusFormat.SINGLE_45 -> stringResource(R.string.format_single_45)
    AcervusFormat.SHELLAC_78 -> stringResource(R.string.format_shellac_78)
    AcervusFormat.OTHER -> stringResource(R.string.format_other)
}

@Composable
private fun RecordingSpeed.label(): String = when (this) {
    RecordingSpeed.RPM_33 -> stringResource(R.string.recording_speed_33)
    RecordingSpeed.RPM_45 -> stringResource(R.string.recording_speed_45)
    RecordingSpeed.RPM_78 -> stringResource(R.string.recording_speed_78)
    RecordingSpeed.OTHER -> stringResource(R.string.recording_speed_other)
}

@Preview(showBackground = true)
@Composable
private fun RecordDetailLoadedPreview() {
    val now = Instant.parse("2026-05-16T10:00:00Z")
    val played = Instant.parse("2026-05-13T18:00:00Z")
    AcervusTheme {
        RecordDetailContent(
            state = RecordDetailUiState.Loaded(
                Record(
                    id = "1",
                    artists = listOf(
                        Artist("a", "Pink Floyd"),
                        Artist("b", "Roger Waters")
                    ),
                    title = "The Dark Side of the Moon",
                    genre = "Rock progressif",
                    year = 1973,
                    label = "Harvest",
                    format = AcervusFormat.LP_33,
                    recordingSpeed = RecordingSpeed.RPM_33,
                    country = "UK",
                    purchasePrice = 35.0,
                    rating = 5,
                    status = RecordStatus.OWNED,
                    lastPlayedAt = played,
                    createdAt = played,
                    updatedAt = now
                )
            ),
            now = now,
            onBack = {},
            onEdit = {},
            onMarkAsPlayed = {},
            onResetLastPlayed = {},
            onWishlistToggle = {},
            onRatingChange = {},
            onDelete = {}
        )
    }
}
