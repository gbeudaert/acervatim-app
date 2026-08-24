package app.acervatim.ui.sharing.received

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import app.acervatim.R

/**
 * Saisie d'un code de partage, plus le nom qu'on veut donner au partage rejoint.
 *
 * Le code arrive **hors-bande** (SMS, messagerie, oral) : c'est un collage, pas une frappe. D'où
 * l'absence de capitalisation automatique — un code est sensible à la casse (base64url) et
 * « corriger » sa première lettre le casserait silencieusement.
 *
 * Le libellé est optionnel et modifiable ensuite : on ne bloque pas l'adhésion sur un champ de
 * confort. Il est **privé** — le propriétaire ne le verra jamais.
 */
@Composable
internal fun JoinShareDialog(
    busy: Boolean,
    onJoin: (code: String, label: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.received_join_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.received_join_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim().take(CODE_MAX) },
                    label = { Text(stringResource(R.string.received_join_code_field)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(LABEL_MAX) },
                    label = { Text(stringResource(R.string.received_label_field)) },
                    supportingText = { Text(stringResource(R.string.received_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(code, label.trim().takeIf { it.isNotEmpty() }) },
                enabled = code.isNotBlank() && !busy
            ) {
                Text(stringResource(R.string.received_join_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Même borne que le serveur (`RedeemShareSchema.code` ≤ 64). */
private const val CODE_MAX = 64

/** Même borne que `SHARE_LABEL_MAX` côté serveur. */
private const val LABEL_MAX = 200
