package app.acervatim.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.ItemStatus
import app.acervatim.ui.theme.AcervatimTheme
import java.time.Instant

@Composable
fun RecordRow(
    entry: RecordArtistEntry,
    modifier: Modifier = Modifier
) {
    val record = entry.record
    val isWishlist = record.status == ItemStatus.WISHLIST
    val rowAlpha = when {
        !isWishlist -> 1f
        isSystemInDarkTheme() -> 0.40f
        else -> 0.45f
    }
    val featuring = entry.featuringArtists

    Row(
        // Hauteur minimale calée sur la jaquette + les marges verticales (64 + 2×8) : sans ça une
        // ligne sans texte long resterait plus basse que sa propre pochette.
        modifier = modifier
            .heightIn(min = 80.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecordCover(
            coverPath = record.coverPath,
            coverUrl = record.coverRemoteUrl,
            contentDescription = "${record.title} – ${entry.displayedArtist.name}",
            cornerRadius = 6.dp,
            cacheKey = record.id,
            // La pochette est l'élément d'identification le plus rapide d'une collection de
            // vinyles : elle porte plus que le titre pour reconnaître un disque.
            modifier = Modifier.size(COVER_SIZE)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.displayedArtist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (featuring.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.collection_row_featuring,
                        featuring.joinToString(", ") { it.name }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Vitesse de gravure plutôt que format : c'est elle qui conditionne l'écoute (une platine
        // ne lit pas un 78 tours comme un 33), là où le format se devine déjà à la pochette.
        record.recordingSpeed?.let { speed ->
            InfoChip(text = speed.shortLabel())
            Spacer(Modifier.width(8.dp))
        }
        if (isWishlist) {
            Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

/** Puce d'information en fin de ligne. Générique : elle ne connaît que le texte à poser. */
@Composable
private fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Libellé court, pour une puce de liste : « 33 tr » et non « 33 tr/min », qui mangerait la place du
 * titre. Le libellé complet reste celui du détail et du formulaire (`recording_speed_*`).
 *
 * Ressources et non chaînes en dur — contrairement à l'ancien libellé de format — parce que « tr »
 * est une abréviation française qui n'a pas à survivre à une traduction.
 */
@Composable
private fun RecordingSpeed.shortLabel(): String = when (this) {
    RecordingSpeed.RPM_33 -> stringResource(R.string.recording_speed_33_short)
    RecordingSpeed.RPM_45 -> stringResource(R.string.recording_speed_45_short)
    RecordingSpeed.RPM_78 -> stringResource(R.string.recording_speed_78_short)
    // Une vitesse renseignée « Autre » reste une information : la masquer la rendrait
    // indistinguable d'une vitesse absente.
    RecordingSpeed.OTHER -> stringResource(R.string.recording_speed_other_short)
}

@Preview(showBackground = true)
@Composable
private fun RecordRowOwnedPreview() {
    val now = Instant.parse("2026-05-16T10:00:00Z")
    val record = Record(
        id = "1",
        artists = listOf(Artist("a1", "Pink Floyd")),
        title = "The Dark Side of the Moon",
        format = AcervatimFormat.LP_33,
        recordingSpeed = RecordingSpeed.RPM_33,
        status = ItemStatus.OWNED,
        createdAt = now,
        updatedAt = now
    )
    AcervatimTheme {
        RecordRow(
            entry = RecordArtistEntry(
                record = record,
                displayedArtist = record.artists.first(),
                featuringArtists = emptyList()
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordRowFeaturingPreview() {
    val now = Instant.parse("2026-05-16T10:00:00Z")
    val primary = Artist("a1", "Daft Punk")
    val feat = listOf(Artist("a2", "Pharrell Williams"), Artist("a3", "Nile Rodgers"))
    val record = Record(
        id = "1",
        artists = listOf(primary) + feat,
        title = "Get Lucky",
        format = AcervatimFormat.SINGLE_45,
        recordingSpeed = RecordingSpeed.RPM_45,
        status = ItemStatus.OWNED,
        createdAt = now,
        updatedAt = now
    )
    AcervatimTheme {
        RecordRow(
            entry = RecordArtistEntry(
                record = record,
                displayedArtist = primary,
                featuringArtists = feat
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordRowWishlistPreview() {
    val now = Instant.parse("2026-05-16T10:00:00Z")
    val record = Record(
        id = "2",
        artists = listOf(Artist("a", "Radiohead")),
        title = "OK Computer",
        format = AcervatimFormat.LP_33,
        recordingSpeed = RecordingSpeed.RPM_33,
        status = ItemStatus.WISHLIST,
        createdAt = now,
        updatedAt = now
    )
    AcervatimTheme {
        RecordRow(
            entry = RecordArtistEntry(
                record = record,
                displayedArtist = record.artists.first(),
                featuringArtists = emptyList()
            )
        )
    }
}

/** Taille de la pochette dans la liste plate. Un cran au-dessus du 48 dp Material par défaut. */
private val COVER_SIZE = 64.dp
