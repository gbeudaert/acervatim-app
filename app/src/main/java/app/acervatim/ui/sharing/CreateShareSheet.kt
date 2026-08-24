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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.acervatim.R
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.SHARE_STATUS_ORDER
import app.acervatim.domain.model.sharing.SharableCollection
import java.time.Duration
import java.time.Instant

/** Durées proposées pour l'expiration du code. `null` = pas d'expiration. */
private enum class ExpiryChoice(val labelRes: Int, val duration: Duration?) {
    NEVER(R.string.sharing_expiry_never, null),
    DAYS_7(R.string.sharing_expiry_7d, Duration.ofDays(7)),
    DAYS_30(R.string.sharing_expiry_30d, Duration.ofDays(30))
}

/**
 * Feuille de création d'un partage : libellé, collections exposées et statuts de chacune, nombre de
 * personnes, expiration.
 *
 * Le geste central a changé avec le sprint backend S5 : on ne choisit plus **une** portée pour
 * **une** collection, on coche pour chaque collection ce qu'elle laisse voir. Un ensemble vide n'est
 * pas une erreur — c'est ainsi qu'on exclut une collection du partage. D'où l'absence de case
 * « inclure » séparée : les trois puces *sont* le choix.
 *
 * Les statuts sont décrits par ce que le membre **verra**, jamais par leur code technique : c'est la
 * seule formulation qui permette de choisir sans se tromper, et un partage mal ciblé expose des
 * données.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CreateShareSheet(
    sharable: List<SharableCollection>,
    busy: Boolean,
    onCreate: (
        entries: Map<String, Set<ItemStatus>>,
        label: String?,
        maxUses: Int,
        expiresAt: Instant?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by rememberSaveable { mutableStateOf("") }
    var maxUses by rememberSaveable { mutableStateOf("1") }
    var expiry by rememberSaveable { mutableStateOf(ExpiryChoice.NEVER) }

    // Sélection par collection. Pré-cochée sur « possédés » : c'est le partage attendu dans le cas
    // courant (le foyer), et un défaut plus large exposerait la wantlist sans qu'on l'ait demandé.
    val selection = remember(sharable) {
        mutableStateMapOf<String, Set<ItemStatus>>().apply {
            sharable.forEach { put(it.collectionId, setOf(ItemStatus.OWNED)) }
        }
    }

    val chosen = selection.filterValues { it.isNotEmpty() }
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

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(LABEL_MAX) },
                label = { Text(stringResource(R.string.sharing_label_field)) },
                supportingText = { Text(stringResource(R.string.sharing_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.sharing_collections_label),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.sharing_collections_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            sharable.forEach { collection ->
                CollectionStatusCard(
                    collection = collection,
                    statuses = selection[collection.collectionId].orEmpty(),
                    onToggle = { status ->
                        val current = selection[collection.collectionId].orEmpty()
                        selection[collection.collectionId] =
                            if (status in current) current - status else current + status
                    }
                )
            }

            HorizontalDivider()

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

            Text(
                text = if (chosen.isEmpty()) {
                    stringResource(R.string.sharing_summary_none)
                } else {
                    pluralStringResource(
                        R.plurals.sharing_summary_collections,
                        chosen.size,
                        chosen.size
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (chosen.isEmpty()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

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
                        onCreate(
                            chosen.toMap(),
                            label.trim().takeIf { it.isNotEmpty() },
                            parsedMaxUses ?: 1,
                            expiresAt
                        )
                    },
                    enabled = maxUsesValid && chosen.isNotEmpty() && !busy
                ) {
                    Text(stringResource(R.string.sharing_create_action))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Une collection et les statuts qu'elle exposera. Zéro puce cochée = collection non partagée, ce
 * que la carte annonce explicitement plutôt que de laisser deviner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionStatusCard(
    collection: SharableCollection,
    statuses: Set<ItemStatus>,
    onToggle: (ItemStatus) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (statuses.isEmpty()) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = collection.name, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SHARE_STATUS_ORDER.forEach { status ->
                    FilterChip(
                        selected = status in statuses,
                        onClick = { onToggle(status) },
                        label = { Text(stringResource(status.shareLabelRes())) }
                    )
                }
            }
            if (statuses.isEmpty()) {
                Text(
                    text = stringResource(R.string.sharing_collection_excluded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Même borne de validation que le backend (`CreateShareSchema.maxUses` ≤ 100). */
private const val MAX_USES_LIMIT = 100

/** Même borne que `SHARE_LABEL_MAX` côté serveur : refuser ici évite un 400 pour un champ de confort. */
private const val LABEL_MAX = 200
