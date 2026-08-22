package app.acervatim.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.sync.SyncStatus
import app.acervatim.domain.usecase.CollectionTypeStats
import app.acervatim.ui.theme.AcervatimTheme

/**
 * Contenu du drawer Material 3 de switch de collection. Liste tous les [CollectionType] définis
 * avec leur compteur d'affichage (items pour FLAT, séries pour SERIAL). L'élément actif est mis
 * en évidence via [NavigationDrawerItem]'s `selected`.
 */
@Composable
internal fun CollectionDrawerContent(
    activeCollectionType: CollectionType,
    visibleCollectionTypes: List<CollectionType>,
    stats: List<CollectionTypeStats>,
    accountHeader: AccountHeaderUiState,
    onCollectionTypeSelected: (CollectionType) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSharing: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp)
        )

        AccountDrawerHeader(state = accountHeader, onOpenAccount = onOpenAccount)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))

        visibleCollectionTypes.forEach { type ->
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
            // Réservé aux comptes premium : le partage suppose une collection synchronisée. Un
            // compte non premium ne voit pas l'entrée plutôt que d'y buter sur un 402.
            if (accountHeader.isPremium == true) {
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Outlined.Share, contentDescription = null) },
                    label = { Text(stringResource(R.string.sharing_open)) },
                    selected = false,
                    onClick = onOpenSharing,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
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

/**
 * En-tête compte du drawer : invite à se connecter si déconnecté, sinon état + sous-ligne sync.
 * Toujours cliquable → écran Compte (l'app reste 100 % utilisable sans connexion).
 */
@Composable
private fun AccountDrawerHeader(
    state: AccountHeaderUiState,
    onOpenAccount: () -> Unit
) {
    val limitReached = state.syncStatus is SyncStatus.LimitReached
    val premiumRequired = state.syncStatus is SyncStatus.PremiumRequired
    val label = if (state.signedIn) {
        stringResource(R.string.drawer_account_signed_in)
    } else {
        stringResource(R.string.drawer_account_signed_out)
    }
    val subLabel = when {
        !state.signedIn -> null
        limitReached -> stringResource(R.string.drawer_account_limit_reached)
        premiumRequired -> stringResource(R.string.drawer_account_premium_required)
        else -> when (val sync = state.syncStatus) {
            SyncStatus.Idle -> stringResource(R.string.sync_status_idle)
            SyncStatus.Running -> stringResource(R.string.sync_status_running)
            is SyncStatus.Success -> stringResource(R.string.sync_status_last, sync.at.toString())
            is SyncStatus.LimitReached -> null // couvert par limitReached
            is SyncStatus.PremiumRequired -> null // couvert par premiumRequired
            is SyncStatus.Failure -> stringResource(R.string.sync_status_error, sync.message)
        }
    }
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = if (state.signedIn) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                contentDescription = null
            )
        },
        label = {
            Column {
                Text(text = label)
                subLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.syncStatus is SyncStatus.Failure) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        selected = false,
        onClick = onOpenAccount,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
    Spacer(Modifier.height(8.dp))
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
            visibleCollectionTypes = CollectionType.entries.toList(),
            stats = listOf(
                CollectionTypeStats(CollectionType.VINYL, recordCount = 128, seriesCount = 0),
                CollectionTypeStats(CollectionType.MANGA, recordCount = 38, seriesCount = 3)
            ),
            accountHeader = AccountHeaderUiState(signedIn = false),
            onCollectionTypeSelected = {},
            onOpenSettings = {},
            onOpenAccount = {},
            onOpenSharing = {}
        )
    }
}
