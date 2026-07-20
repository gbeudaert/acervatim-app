package app.acervatim.ui.list

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.usecase.BulkEditDelta
import app.acervatim.domain.usecase.FieldEdit

/** État tri-état d'un champ de la feuille d'édition groupée (§2.2). */
private enum class EditMode { KEEP, SET, CLEAR }

/**
 * Feuille d'édition groupée (§2.2, lot L3) : un contrôle tri-état par champ groupable, filtré par
 * capability du [CollectionType] actif. Émet un [BulkEditDelta] ne portant que les champs en
 * « Définir »/« Effacer ». Le titre et les identifiants uniques n'y figurent jamais (§3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BulkEditSheet(
    collectionType: CollectionType,
    onApply: (BulkEditDelta) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var statusMode by remember { mutableStateOf(EditMode.KEEP) }
    var statusValue by remember { mutableStateOf(ItemStatus.OWNED) }

    var ratingMode by remember { mutableStateOf(EditMode.KEEP) }
    var ratingValue by remember { mutableStateOf(0) }

    var genreMode by remember { mutableStateOf(EditMode.KEEP) }
    var genreValue by remember { mutableStateOf("") }

    var formatMode by remember { mutableStateOf(EditMode.KEEP) }
    var formatValue by remember { mutableStateOf(AcervatimFormat.LP_33) }

    var publisherMode by remember { mutableStateOf(EditMode.KEEP) }
    var publisherValue by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.bulk_edit_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Statut — cas d'usage n°1 (wishlist → possédé). Non nullable : pas d'option « Effacer ».
            BulkField(
                label = stringResource(R.string.bulk_edit_field_status),
                mode = statusMode,
                nullable = false,
                onMode = { statusMode = it }
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(
                        ItemStatus.OWNED to R.string.bulk_edit_status_owned,
                        ItemStatus.WISHLIST to R.string.bulk_edit_status_wishlist
                    )
                    options.forEachIndexed { i, (value, res) ->
                        SegmentedButton(
                            selected = statusValue == value,
                            onClick = { statusValue = value },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)
                        ) { Text(stringResource(res)) }
                    }
                }
            }

            BulkField(
                label = stringResource(R.string.bulk_edit_field_rating),
                mode = ratingMode,
                nullable = true,
                onMode = { ratingMode = it }
            ) {
                RatingChips(selected = ratingValue, onSelect = { ratingValue = it })
            }

            BulkField(
                label = stringResource(R.string.field_genre),
                mode = genreMode,
                nullable = true,
                onMode = { genreMode = it }
            ) {
                OutlinedTextField(
                    value = genreValue,
                    onValueChange = { genreValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (collectionType == CollectionType.VINYL) {
                BulkField(
                    label = stringResource(R.string.field_format),
                    mode = formatMode,
                    nullable = true,
                    onMode = { formatMode = it }
                ) {
                    FormatChips(selected = formatValue, onSelect = { formatValue = it })
                }
            }

            if (collectionType == CollectionType.MANGA) {
                BulkField(
                    label = stringResource(R.string.bulk_edit_field_publisher),
                    mode = publisherMode,
                    nullable = true,
                    onMode = { publisherMode = it }
                ) {
                    OutlinedTextField(
                        value = publisherValue,
                        onValueChange = { publisherValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.bulk_edit_cancel))
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(onClick = {
                    onApply(
                        BulkEditDelta(
                            status = statusMode.toEdit { statusValue },
                            rating = ratingMode.toEdit { ratingValue },
                            genre = genreMode.toEdit { genreValue.trim().takeIf(String::isNotEmpty) },
                            format = if (collectionType == CollectionType.VINYL) {
                                formatMode.toEdit { formatValue }
                            } else FieldEdit.Keep,
                            publisherFr = if (collectionType == CollectionType.MANGA) {
                                publisherMode.toEdit { publisherValue.trim().takeIf(String::isNotEmpty) }
                            } else FieldEdit.Keep
                        )
                    )
                }) {
                    Text(stringResource(R.string.bulk_edit_apply))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Section d'un champ groupable : libellé, sélecteur tri-état, et l'éditeur de valeur si « Définir ». */
@Composable
private fun BulkField(
    label: String,
    mode: EditMode,
    nullable: Boolean,
    onMode: (EditMode) -> Unit,
    setEditor: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        ModeSelector(mode = mode, nullable = nullable, onChange = onMode)
        if (mode == EditMode.SET) setEditor()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(mode: EditMode, nullable: Boolean, onChange: (EditMode) -> Unit) {
    val options = buildList {
        add(EditMode.KEEP to R.string.bulk_edit_field_keep)
        add(EditMode.SET to R.string.bulk_edit_field_set)
        if (nullable) add(EditMode.CLEAR to R.string.bulk_edit_field_clear)
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, res) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)
            ) { Text(stringResource(res)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        (0..5).forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.toString()) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatChips(selected: AcervatimFormat, onSelect: (AcervatimFormat) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AcervatimFormat.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(stringResource(f.bulkLabelRes())) }
            )
        }
    }
}

private fun AcervatimFormat.bulkLabelRes(): Int = when (this) {
    AcervatimFormat.LP_33 -> R.string.format_lp_33
    AcervatimFormat.SINGLE_45 -> R.string.format_single_45
    AcervatimFormat.SHELLAC_78 -> R.string.format_shellac_78
    AcervatimFormat.OTHER -> R.string.format_other
}

/** Projette le mode tri-état en [FieldEdit] : SET n'émet que si la valeur est présente (sinon Keep). */
private fun <T> EditMode.toEdit(value: () -> T?): FieldEdit<T> = when (this) {
    EditMode.KEEP -> FieldEdit.Keep
    EditMode.CLEAR -> FieldEdit.Clear
    EditMode.SET -> value()?.let { FieldEdit.Set(it) } ?: FieldEdit.Keep
}
