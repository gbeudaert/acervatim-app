package fr.beudaert.acervatim.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.beudaert.acervatim.R
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.usecase.CollectionTypeStats
import fr.beudaert.acervatim.ui.theme.AcervatimTheme

/**
 * Contenu du drawer Material 3 de switch de collection. Liste tous les [CollectionType] définis
 * avec leur compteur d'affichage (items pour FLAT, séries pour SERIAL). L'élément actif est mis
 * en évidence via [NavigationDrawerItem]'s `selected`.
 */
@Composable
internal fun CollectionDrawerContent(
    activeCollectionType: CollectionType,
    stats: List<CollectionTypeStats>,
    onCollectionTypeSelected: (CollectionType) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp)
        )

        CollectionType.entries.forEach { type ->
            val typeStats = stats.firstOrNull { it.collectionType == type }
            val displayCount = typeStats?.displayCount ?: 0
            NavigationDrawerItem(
                icon = { Icon(imageVector = type.icon(), contentDescription = null) },
                label = { Text(text = stringResource(type.labelRes())) },
                badge = { Text(text = displayCount.toString()) },
                selected = type == activeCollectionType,
                onClick = { onCollectionTypeSelected(type) },
                colors = NavigationDrawerItemDefaults.colors(),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        Column {
            NavigationDrawerItem(
                icon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.collection_open_settings)) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            // "Nouvelle collection" est intentionnellement absent : tant qu'il n'y a qu'un seul
            // nouveau type à proposer (déjà ajouté ici), cet item serait un piège visuel.
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun CollectionType.labelRes(): Int = when (this) {
    CollectionType.VINYL -> R.string.collection_type_vinyl
    CollectionType.MANGA -> R.string.collection_type_manga
}

private fun CollectionType.icon(): ImageVector = when (this) {
    CollectionType.VINYL -> Icons.Filled.Album
    CollectionType.MANGA -> Icons.Filled.Book
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun CollectionDrawerPreview() {
    AcervatimTheme {
        CollectionDrawerContent(
            activeCollectionType = CollectionType.VINYL,
            stats = listOf(
                CollectionTypeStats(CollectionType.VINYL, recordCount = 128, seriesCount = 0),
                CollectionTypeStats(CollectionType.MANGA, recordCount = 38, seriesCount = 3)
            ),
            onCollectionTypeSelected = {},
            onOpenSettings = {}
        )
    }
}
