package app.acervatim.ui.series

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.ui.components.RecordCover

/**
 * Vignette compacte d'un tome dans la grille « Détail série ».
 *
 * Visuel par statut :
 * - **OWNED** : jaquette pleine opacité, pas d'overlay.
 * - **WISHLIST** : opacité 0.55, icône cœur superposée en haut-droite, badge en couleur primaire.
 * - **IGNORED** : opacité 0.30, icône « interdit » superposée en haut-droite, titre barré.
 *
 * Tap = ouvre le détail du tome. Tap long = menu contextuel pour changer le statut.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VolumeTile(
    record: Record,
    onClick: () -> Unit,
    onSetStatus: (ItemStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val alpha = when (record.status) {
        ItemStatus.OWNED -> 1f
        ItemStatus.WISHLIST -> if (isSystemInDarkTheme()) 0.50f else 0.55f
        ItemStatus.IGNORED -> if (isSystemInDarkTheme()) 0.28f else 0.30f
    }

    Box(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true }
            )
    ) {
        Column(modifier = Modifier.alpha(alpha)) {
            RecordCover(
                coverPath = record.coverPath,
                contentDescription = record.title,
                cornerRadius = 8.dp,
                cacheKey = record.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
            )
            Text(
                text = record.volumeNumber?.let {
                    stringResource(R.string.series_detail_volume_label, it)
                } ?: stringResource(R.string.series_detail_volume_no_number),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                textDecoration = if (record.status == ItemStatus.IGNORED) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        StatusBadge(
            status = record.status,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        )

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.item_status_owned)) },
                onClick = {
                    onSetStatus(ItemStatus.OWNED)
                    menuOpen = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.item_status_wishlist)) },
                onClick = {
                    onSetStatus(ItemStatus.WISHLIST)
                    menuOpen = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.item_status_ignored)) },
                onClick = {
                    onSetStatus(ItemStatus.IGNORED)
                    menuOpen = false
                }
            )
        }
    }
}

@Composable
private fun StatusBadge(status: ItemStatus, modifier: Modifier = Modifier) {
    val icon = when (status) {
        ItemStatus.OWNED -> return
        ItemStatus.WISHLIST -> Icons.Outlined.FavoriteBorder
        ItemStatus.IGNORED -> Icons.Outlined.Block
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
