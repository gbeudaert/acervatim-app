package fr.beudaert.acervatim.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.beudaert.acervatim.R
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.Series
import fr.beudaert.acervatim.domain.model.SeriesWithCounts
import fr.beudaert.acervatim.ui.components.RecordCover
import fr.beudaert.acervatim.ui.theme.AcervatimTheme
import java.time.Instant

/**
 * Grille de séries pour la vue SERIAL. Chaque tile montre la jaquette, le titre et un compteur
 * "X / Y tomes" où :
 * - X = volumes OWNED en bibliothèque
 * - Y = totalVolumes annoncé (si présent), sinon nombre de volumes effectivement enregistrés
 */
@Composable
internal fun SeriesGrid(
    items: List<SeriesWithCounts>,
    onOpenSeries: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(items = items, key = { it.series.id }) { item ->
            SeriesGridTile(
                item = item,
                onClick = { onOpenSeries(item.series.id) }
            )
        }
    }
}

@Composable
internal fun SeriesGridTile(
    item: SeriesWithCounts,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalKnown = item.series.totalVolumes ?: item.totalVolumesInLibrary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        RecordCover(
            coverPath = item.series.coverPath,
            contentDescription = item.series.title,
            cornerRadius = 8.dp,
            cacheKey = "series-${item.series.id}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.series.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(
                R.string.series_grid_volume_count,
                item.ownedVolumes,
                totalKnown
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SeriesGridTilePreview() {
    val now = Instant.parse("2026-05-19T08:00:00Z")
    val item = SeriesWithCounts(
        series = Series(
            id = "s1",
            collectionType = CollectionType.MANGA,
            title = "Berserk",
            originalAuthor = "Kentarō Miura",
            publicationStatus = "En cours",
            totalVolumes = 42,
            synopsis = null,
            coverPath = null,
            createdAt = now,
            updatedAt = now
        ),
        ownedVolumes = 28,
        wishlistVolumes = 10,
        ignoredVolumes = 0,
        totalVolumesInLibrary = 38
    )
    AcervatimTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SeriesGridTile(item = item, onClick = {})
        }
    }
}
