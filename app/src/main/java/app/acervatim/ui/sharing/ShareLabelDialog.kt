package app.acervatim.ui.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.acervatim.R

/**
 * Renomme un partage — celui qu'on a émis comme celui qu'on a rejoint. Le libellé est **privé de
 * chaque côté** : le propriétaire ne voit pas celui du membre, et réciproquement. D'où un seul
 * dialogue pour les deux usages, avec le texte d'aide qui change.
 *
 * Champ vidé ⇒ le libellé est effacé (le serveur normalise une chaîne blanche en `null`), ce qui
 * fait de « effacer » un geste normal plutôt qu'une action séparée.
 */
@Composable
internal fun ShareLabelDialog(
    current: String?,
    hintRes: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(current.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharing_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(LABEL_MAX) },
                    label = { Text(stringResource(R.string.sharing_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Même borne que `SHARE_LABEL_MAX` côté serveur. */
private const val LABEL_MAX = 200
