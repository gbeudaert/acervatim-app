package app.acervatim.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.acervatim.R

/**
 * Dialog d'options du rafraîchissement groupé (§2.5.2) : par défaut on comble les trous (jaquette
 * absente / description vide) ; une case permet de forcer le remplacement des valeurs existantes.
 * Émet [onConfirm] avec le mode « remplacer » choisi.
 */
@Composable
internal fun RefreshOptionDialog(
    onConfirm: (overwrite: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var overwrite by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refresh_option_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                    Text(
                        text = stringResource(R.string.refresh_overwrite),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (overwrite) {
                    Text(
                        text = stringResource(R.string.refresh_overwrite_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(overwrite) }) {
                Text(stringResource(R.string.refresh_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
