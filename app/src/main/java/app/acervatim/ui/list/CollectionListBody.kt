package app.acervatim.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.ListMode
import app.acervatim.ui.components.RecordRow

/**
 * Corps de liste d'une collection — **le seul**, partagé par ses propres collections
 * ([CollectionScreen]) et par celles reçues en partage
 * ([app.acervatim.ui.sharing.received.SharedCollectionScreen]).
 *
 * C'est délibérément un fichier à part : tant que ce rendu vivait dans `CollectionScreen`, tout ce
 * qu'on y ajoutait (sections par lettre, tri, densité) restait invisible du partage, et deux vues
 * censées être identiques divergeaient à chaque itération. Les deux écrans construisent le même
 * [CollectionUiState] et le donnent à lire ici ; ce qui les distingue — barre du haut, bouton
 * d'ajout, mode sélection — reste chez eux.
 *
 * Rien ici ne connaît la provenance des données : ni Room, ni réseau, ni propriétaire.
 */

@Composable
internal fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun EmptyState(state: CollectionUiState.Empty) {
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
internal fun ContentList(
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
internal fun SerialContentList(
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
        if (state.displayMode == DisplayMode.LIST) {
            SeriesList(
                items = state.seriesList,
                onOpenSeries = onSeriesClick,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onEnterSelection = onEnterSelection,
                onToggleSelection = onToggleSelection
            )
        } else {
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
}

@Composable
private fun SectionHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
