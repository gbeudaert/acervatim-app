package fr.beudaert.acervus.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.beudaert.acervus.R
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ListMode
import fr.beudaert.acervus.domain.model.RecordFilter
import fr.beudaert.acervus.domain.model.StatusFilter
import fr.beudaert.acervus.ui.theme.AcervusTheme

/**
 * Le toggle FLAT / SERIAL n'est exposé que si au moins un [CollectionType] supporte SERIAL,
 * conformément à la spec Sprint 7 (« visible selon CollectionType »). Tant qu'une collection
 * compatible n'est pas activée, on reste implicitement en FLAT.
 */
private val SERIAL_TOGGLE_VISIBLE: Boolean =
    CollectionType.entries.any { it.supportsSerial }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    initial: RecordFilter,
    availableGenres: List<String>,
    onApply: (
        listMode: ListMode,
        status: StatusFilter,
        genre: String?,
        format: AcervusFormat?,
        notPlayedSinceSixMonths: Boolean
    ) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var listMode by rememberSaveable(initial) { mutableStateOf(initial.listMode) }
    var status by rememberSaveable(initial) { mutableStateOf(initial.status) }
    var genre by rememberSaveable(initial) { mutableStateOf(initial.genre) }
    var format by rememberSaveable(initial) { mutableStateOf(initial.format) }
    var notPlayed by rememberSaveable(initial) { mutableStateOf(initial.notPlayedSinceSixMonths) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        FilterSheetContent(
            listMode = listMode,
            status = status,
            genre = genre,
            format = format,
            notPlayedSinceSixMonths = notPlayed,
            availableGenres = availableGenres,
            onListModeChange = { listMode = it },
            onStatusChange = { status = it },
            onGenreChange = { genre = it },
            onFormatChange = { format = it },
            onNotPlayedChange = { notPlayed = it },
            onApply = {
                onApply(listMode, status, genre, format, notPlayed)
            },
            onReset = {
                listMode = ListMode.FLAT
                status = StatusFilter.ALL
                genre = null
                format = null
                notPlayed = false
                onReset()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    listMode: ListMode,
    status: StatusFilter,
    genre: String?,
    format: AcervusFormat?,
    notPlayedSinceSixMonths: Boolean,
    availableGenres: List<String>,
    onListModeChange: (ListMode) -> Unit,
    onStatusChange: (StatusFilter) -> Unit,
    onGenreChange: (String?) -> Unit,
    onFormatChange: (AcervusFormat?) -> Unit,
    onNotPlayedChange: (Boolean) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.filters_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (SERIAL_TOGGLE_VISIBLE) {
            SectionLabel(stringResource(R.string.filters_list_mode))
            ListModeSegmented(selected = listMode, onSelect = onListModeChange)
        }

        SectionLabel(stringResource(R.string.filters_status))
        StatusSegmented(selected = status, onSelect = onStatusChange)

        SectionLabel(stringResource(R.string.field_genre))
        GenreDropdown(
            selected = genre,
            options = availableGenres,
            onSelect = onGenreChange
        )

        SectionLabel(stringResource(R.string.field_format))
        FormatChips(selected = format, onSelect = onFormatChange)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.filters_not_played_6m),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Switch(checked = notPlayedSinceSixMonths, onCheckedChange = onNotPlayedChange)
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.filters_reset))
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Button(onClick = onApply) {
                Text(stringResource(R.string.filters_apply))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListModeSegmented(
    selected: ListMode,
    onSelect: (ListMode) -> Unit
) {
    val options = remember {
        listOf(
            ListMode.FLAT to R.string.filters_list_mode_flat,
            ListMode.SERIAL to R.string.filters_list_mode_serial
        )
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, labelRes) ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSegmented(
    selected: StatusFilter,
    onSelect: (StatusFilter) -> Unit
) {
    val options = remember {
        listOf(
            StatusFilter.ALL to R.string.filters_status_all,
            StatusFilter.OWNED_ONLY to R.string.filters_status_owned,
            StatusFilter.WISHLIST_ONLY to R.string.filters_status_wishlist
        )
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, labelRes) ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreDropdown(
    selected: String?,
    options: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labelAll = stringResource(R.string.filters_genre_all)
    val displayValue = selected ?: labelAll

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_genre)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(labelAll) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatChips(
    selected: AcervusFormat?,
    onSelect: (AcervusFormat?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filters_format_all)) }
        )
        AcervusFormat.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(if (selected == f) null else f) },
                label = { Text(stringResource(f.labelRes())) }
            )
        }
    }
}

private fun AcervusFormat.labelRes(): Int = when (this) {
    AcervusFormat.LP_33 -> R.string.format_lp_33
    AcervusFormat.SINGLE_45 -> R.string.format_single_45
    AcervusFormat.SHELLAC_78 -> R.string.format_shellac_78
    AcervusFormat.OTHER -> R.string.format_other
}

@Preview(showBackground = true)
@Composable
private fun FilterSheetContentPreview() {
    AcervusTheme {
        Box(modifier = Modifier.padding(8.dp)) {
            FilterSheetContent(
                listMode = ListMode.FLAT,
                status = StatusFilter.ALL,
                genre = null,
                format = null,
                notPlayedSinceSixMonths = false,
                availableGenres = listOf("Rock", "Jazz", "Électro"),
                onListModeChange = {},
                onStatusChange = {},
                onGenreChange = {},
                onFormatChange = {},
                onNotPlayedChange = {},
                onApply = {},
                onReset = {}
            )
        }
    }
}
