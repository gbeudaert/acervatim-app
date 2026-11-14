package app.acervatim.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.ui.components.ZoomableCover

/**
 * Corps du détail d'une série — **le seul**, partagé par ses propres séries ([SeriesDetailScreen])
 * et par celles reçues en partage
 * ([app.acervatim.ui.sharing.received.SharedSeriesDetailScreen]).
 *
 * Même raison d'être que `ui/list/CollectionListBody.kt` : tant que ce rendu vivait dans son écran,
 * il était inatteignable depuis le partage, et deux vues censées être identiques divergeaient à
 * chaque itération.
 *
 * Il ne connaît ni la provenance des données, ni le mode sélection — dont les paramètres par défaut
 * suffisent à le désactiver pour un lecteur qui n'a pas le droit d'écrire.
 */

@Composable
internal fun SeriesDetailBody(
    series: Series,
    volumes: List<Record>,
    onOpenVolume: (String) -> Unit,
    selectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onEnterSelection: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    /**
     * Encart placé sous l'en-tête — la bannière des nouveaux tomes (AD3) pour ses propres séries.
     * `null` pour une série reçue en partage : un lecteur n'a rien à ajouter à la wishlist d'autrui.
     */
    banner: (@Composable () -> Unit)? = null
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
        if (banner != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { banner() }
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
                selectionMode = selectionMode,
                selected = record.id in selectedIds,
                onLongClick = { onEnterSelection(record.id) },
                onToggle = { onToggleSelection(record.id) }
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
            ZoomableCover(
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
