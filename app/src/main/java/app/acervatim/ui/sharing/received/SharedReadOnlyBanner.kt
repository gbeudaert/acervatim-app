package app.acervatim.ui.sharing.received

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.ItemStatus
import app.acervatim.ui.sharing.shareScopeSummary

/**
 * Marqueur permanent : cette vue est partagée et en lecture seule.
 *
 * **Permanent, et pas un message d'accueil** : sans lui, un membre qui ne voit que les possédés
 * conclura que la collection est incomplète ou buguée. Il reste donc affiché en profondeur, jusque
 * dans le détail d'une série — quitter la liste ne doit pas faire oublier qu'on est chez quelqu'un
 * d'autre.
 *
 * [statuses] vide ⇒ seule la ligne « lecture seule » s'affiche. C'est le cas des écrans de détail,
 * qui ne rechargent pas la portée pour l'afficher : mieux vaut ne rien dire que d'annoncer une portée
 * qu'on n'a pas vérifiée.
 */
@Composable
internal fun SharedReadOnlyBanner(statuses: Set<ItemStatus> = emptySet()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Column {
            Text(
                text = stringResource(R.string.shared_readonly_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (statuses.isNotEmpty()) {
                Text(
                    text = shareScopeSummary(statuses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
