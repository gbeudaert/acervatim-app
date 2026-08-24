package app.acervatim.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.ui.components.RecordCover

/**
 * Vue séries en **liste** — le pendant de [SeriesGrid] pour `DisplayMode.LIST`.
 *
 * Même contenu, même sélection, même compteur de tomes : seule la densité change. La grille
 * reste le défaut d'une collection de séries (cf. `defaultDisplayMode`) ; cette vue existe pour
 * que le choix liste / grille / galerie s'applique aux deux types de collection, pas seulement
 * aux collections plates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesList(
    items: List<SeriesWithCounts>,
    onOpenSeries: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onEnterSelection: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        items(items = items, key = { it.series.id }) { item ->
            val id = item.series.id
            val selected = id in selectedIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            Modifier
                        }
                    )
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelection(id) else onOpenSeries(id) },
                        onLongClick = { if (!selectionMode) onEnterSelection(id) }
                    )
                    .heightIn(min = 80.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelection(id) })
                    Spacer(Modifier.width(8.dp))
                }
                RecordCover(
                    coverPath = item.series.coverPath,
                    coverUrl = item.series.coverRemoteUrl,
                    contentDescription = item.series.title,
                    cornerRadius = 6.dp,
                    cacheKey = "series-${item.series.id}",
                    modifier = Modifier.size(width = 46.dp, height = 64.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.series.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.series_grid_volume_count,
                            item.ownedVolumes,
                            item.series.totalVolumes ?: item.totalVolumesInLibrary
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
