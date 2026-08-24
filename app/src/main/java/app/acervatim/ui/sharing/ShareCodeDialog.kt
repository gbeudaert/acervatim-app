package app.acervatim.ui.sharing

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.sharing.CreatedShare

/**
 * Affiche le code d'un partage — **la seule et unique fois**. Le serveur n'en stocke que le HMAC :
 * quitter ce dialogue le perd définitivement, et l'écran doit le dire au lieu de laisser croire
 * qu'on le retrouvera dans la liste.
 *
 * D'où les deux actions mises en avant : copier et envoyer tout de suite. Le code n'est ni
 * journalisé, ni sauvegardé (`remember` seulement, jamais `rememberSaveable` ni `SavedStateHandle`).
 */
@Composable
internal fun ShareCodeDialog(
    created: CreatedShare,
    onDismiss: () -> Unit
) {
    val share = created.share
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharing_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Rappel de ce que le code ouvre : c'est le dernier moment où le propriétaire peut
                // s'apercevoir qu'il expose plus qu'il ne voulait — après, seule la révocation reste.
                share.collections.forEach { entry ->
                    Text(
                        text = stringResource(
                            R.string.sharing_code_exposes,
                            entry.name,
                            shareScopeSummary(entry.statuses)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = created.code,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.sharing_code_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(created.code))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.sharing_code_copy))
                }
                if (copied) {
                    Text(
                        text = stringResource(R.string.sharing_code_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                context.getString(R.string.sharing_code_send_subject)
                            )
                            putExtra(Intent.EXTRA_TEXT, created.code)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.sharing_code_send))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sharing_code_done)) }
        }
    )
}
