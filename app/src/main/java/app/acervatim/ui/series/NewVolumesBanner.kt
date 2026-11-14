package app.acervatim.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.EditionTome

/**
 * Bannière du **rattrapage** (AD3) : des tomes de l'édition sont parus depuis l'import. On les
 * **propose** — rien n'est créé sans l'accord de l'utilisateur, la wishlist reste la sienne.
 *
 * La mention de délai n'est pas décorative : la BnF catalogue un tome des semaines après sa sortie,
 * l'utilisateur doit savoir qu'un tome paru hier n'est pas encore attendu ici.
 */
@Composable
internal fun NewVolumesBanner(
    tomes: List<EditionTome>,
    adding: Boolean,
    onAdd: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = pluralStringResource(R.plurals.series_catch_up_title, tomes.size, tomes.size)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        // Annoncée par TalkBack à son apparition : elle arrive après coup, quand la vérification
        // d'arrière-plan aboutit, l'écran étant déjà lu.
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(
                    R.string.series_catch_up_volumes,
                    tomes.joinToString(", ") { it.editionVolume.toString() }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.series_catch_up_delay_note),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onLater, enabled = !adding) {
                    Text(stringResource(R.string.series_catch_up_later))
                }
                Button(onClick = onAdd, enabled = !adding) {
                    if (adding) {
                        val addingLabel = stringResource(R.string.series_catch_up_adding)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = addingLabel }
                        )
                    } else {
                        Text(stringResource(R.string.series_catch_up_add))
                    }
                }
            }
        }
    }
}
