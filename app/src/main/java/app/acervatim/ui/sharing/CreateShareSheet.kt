package app.acervatim.ui.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.sharing.ShareScope
import java.time.Duration
import java.time.Instant

/** Durées proposées pour l'expiration du code. `null` = pas d'expiration. */
private enum class ExpiryChoice(val labelRes: Int, val duration: Duration?) {
    NEVER(R.string.sharing_expiry_never, null),
    DAYS_7(R.string.sharing_expiry_7d, Duration.ofDays(7)),
    DAYS_30(R.string.sharing_expiry_30d, Duration.ofDays(30))
}

/**
 * Feuille de création d'un partage : portée, nombre de personnes, expiration.
 *
 * La portée est décrite par ce que le membre **verra**, pas par son code technique : c'est la seule
 * formulation qui permette de choisir sans se tromper (et un partage mal ciblé expose des données).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CreateShareSheet(
    busy: Boolean,
    onCreate: (scope: ShareScope, maxUses: Int, expiresAt: Instant?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scope by rememberSaveable { mutableStateOf(ShareScope.OWNED) }
    var maxUses by rememberSaveable { mutableStateOf("1") }
    var expiry by rememberSaveable { mutableStateOf(ExpiryChoice.NEVER) }

    val parsedMaxUses = maxUses.toIntOrNull()
    val maxUsesValid = parsedMaxUses != null && parsedMaxUses in 1..MAX_USES_LIMIT

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.sharing_create_action),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.sharing_scope_label),
                style = MaterialTheme.typography.titleSmall
            )
            ShareScope.entries.forEach { candidate ->
                ScopeOption(
                    scope = candidate,
                    selected = candidate == scope,
                    onSelect = { scope = candidate }
                )
            }

            OutlinedTextField(
                value = maxUses,
                onValueChange = { input -> maxUses = input.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.sharing_max_uses_label)) },
                supportingText = { Text(stringResource(R.string.sharing_max_uses_hint)) },
                isError = maxUses.isNotEmpty() && !maxUsesValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.sharing_expiry_label),
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpiryChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = choice == expiry,
                        onClick = { expiry = choice },
                        label = { Text(stringResource(choice.labelRes)) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(
                    onClick = {
                        val expiresAt = expiry.duration?.let { Instant.now().plus(it) }
                        onCreate(scope, parsedMaxUses ?: 1, expiresAt)
                    },
                    enabled = maxUsesValid && !busy
                ) {
                    Text(stringResource(R.string.sharing_create_action))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScopeOption(
    scope: ShareScope,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = stringResource(scope.labelRes()),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(scope.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Même borne de validation que le backend (`CreateShareSchema.maxUses` ≤ 100). */
private const val MAX_USES_LIMIT = 100
