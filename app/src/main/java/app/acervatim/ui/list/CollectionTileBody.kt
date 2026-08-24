package app.acervatim.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.ui.components.RecordCover

/**
 * Corps des vues **en tuiles** — grille et galerie, un seul rendu.
 *
 * Les deux ne diffèrent que par deux choses : la taille des tuiles et la présence de rubriques.
 * Les écrire séparément aurait dupliqué la gestion des jaquettes, du placeholder et du
 * dimensionnement, et fait diverger la vue disques de la vue séries — exactement ce que le
 * sprint demande d'éviter (« ne pas dupliquer l'écran : partager les composants »).
 */
@Composable
internal fun TileContentBody(
    state: CollectionUiState.TileContent,
    onTileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gallery = state.displayMode == DisplayMode.GALLERY
    // Une galerie montre de grandes images ; une grille en montre beaucoup.
    val minCellSize = if (gallery) 150.dp else 110.dp
    // Un disque est carré, un tome ne l'est pas : la tuile suit la forme de l'objet.
    val ratio = if (state.activeCollectionType == CollectionType.MANGA) 0.72f else 1f

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(
                if (state.activeCollectionType == CollectionType.MANGA) {
                    R.string.collection_counter_manga
                } else {
                    R.string.collection_counter
                },
                state.ownedCount,
                state.wishlistCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minCellSize),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            state.sections.forEach { section ->
                // La grille n'a qu'une rubrique, et pas de titre : elle *est* la collection.
                if (gallery) {
                    item(
                        key = "header_${section.kind}",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Text(
                            text = stringResource(section.kind.labelRes()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                items(
                    count = section.tiles.size,
                    key = { index -> "${section.kind}_${section.tiles[index].id}" }
                ) { index ->
                    val tile = section.tiles[index]
                    CoverTile(
                        tile = tile,
                        ratio = ratio,
                        // Le compteur n'est affiché que là où il justifie le classement.
                        showPlayCount = section.kind == TileSectionKind.MOST_PLAYED,
                        onClick = { onTileClick(tile.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverTile(
    tile: CollectionTile,
    ratio: Float,
    showPlayCount: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // `RecordCover` charge à la taille du composable (Coil dimensionne sur les contraintes) et
        // pose un placeholder quand aucune source n'existe : une tuile sans jaquette ne troue
        // pas la grille.
        RecordCover(
            coverPath = tile.coverPath,
            coverUrl = tile.coverUrl,
            contentDescription = tile.title,
            cornerRadius = 8.dp,
            cacheKey = tile.cacheKey,
            placeholderIconSize = 36.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = tile.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val caption = when {
            showPlayCount && tile.playCount > 0 ->
                pluralStringResource(R.plurals.detail_play_count, tile.playCount, tile.playCount)
            else -> tile.subtitle
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Bascule de densité (liste / grille / galerie). Un menu plutôt qu'un bouton cyclique : les trois
 * modes sont visibles d'un coup, et celui en cours est nommé plutôt que deviné.
 */
@Composable
internal fun DisplayModeAction(
    displayMode: DisplayMode,
    onDisplayModeSelected: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(
            imageVector = displayMode.icon(),
            contentDescription = stringResource(R.string.collection_display_mode)
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DisplayMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(stringResource(mode.labelRes())) },
                leadingIcon = { Icon(imageVector = mode.icon(), contentDescription = null) },
                onClick = {
                    expanded = false
                    if (mode != displayMode) onDisplayModeSelected(mode)
                }
            )
        }
    }
}

private fun DisplayMode.icon(): ImageVector = when (this) {
    DisplayMode.LIST -> Icons.Outlined.ViewList
    DisplayMode.GRID -> Icons.Outlined.GridView
    DisplayMode.GALLERY -> Icons.Outlined.PhotoLibrary
}

private fun DisplayMode.labelRes(): Int = when (this) {
    DisplayMode.LIST -> R.string.display_mode_list
    DisplayMode.GRID -> R.string.display_mode_grid
    DisplayMode.GALLERY -> R.string.display_mode_gallery
}

private fun TileSectionKind.labelRes(): Int = when (this) {
    TileSectionKind.ALL -> R.string.gallery_section_all
    TileSectionKind.RECENTLY_PLAYED -> R.string.gallery_section_recently_played
    TileSectionKind.MOST_PLAYED -> R.string.gallery_section_most_played
    TileSectionKind.FORGOTTEN -> R.string.gallery_section_forgotten
}
