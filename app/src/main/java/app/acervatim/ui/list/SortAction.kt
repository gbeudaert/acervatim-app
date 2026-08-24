package app.acervatim.ui.list

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.acervatim.R
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.RecordSortField
import app.acervatim.domain.model.SortDirection

/**
 * Sélecteur de tri de la barre de recherche.
 *
 * Volontairement **séparé** du panneau de filtres : ordonner et restreindre sont deux gestes
 * distincts, et le tri se change bien plus souvent qu'un filtre — l'enfouir derrière une feuille
 * modale à valider le rendrait pénible. Le champ actif porte une coche, et le rechoisir inverse
 * le sens ; la dernière ligne inverse sans changer de champ, pour qui préfère viser ça.
 */
@Composable
internal fun SortAction(
    sort: RecordSort,
    onSortFieldSelected: (RecordSortField) -> Unit,
    onSortDirectionToggled: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Sort,
            contentDescription = stringResource(R.string.collection_sort)
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        RecordSortField.entries.forEach { field ->
            val active = field == sort.field
            DropdownMenuItem(
                text = { Text(stringResource(field.labelRes())) },
                leadingIcon = {
                    if (active) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null
                        )
                    }
                },
                trailingIcon = {
                    if (active) {
                        Icon(
                            imageVector = sort.direction.icon(),
                            contentDescription = stringResource(sort.direction.labelRes())
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onSortFieldSelected(field)
                }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(sort.direction.toggled().labelRes())) },
            leadingIcon = {
                Icon(
                    imageVector = sort.direction.toggled().icon(),
                    contentDescription = null
                )
            },
            onClick = {
                expanded = false
                onSortDirectionToggled()
            }
        )
    }
}

private fun SortDirection.icon() = when (this) {
    SortDirection.ASCENDING -> Icons.Outlined.ArrowUpward
    SortDirection.DESCENDING -> Icons.Outlined.ArrowDownward
}

private fun SortDirection.labelRes(): Int = when (this) {
    SortDirection.ASCENDING -> R.string.sort_direction_ascending
    SortDirection.DESCENDING -> R.string.sort_direction_descending
}

private fun RecordSortField.labelRes(): Int = when (this) {
    RecordSortField.ARTIST -> R.string.sort_field_artist
    RecordSortField.TITLE -> R.string.sort_field_title
    RecordSortField.YEAR -> R.string.sort_field_year
    RecordSortField.LAST_PLAYED -> R.string.sort_field_last_played
    RecordSortField.ADDED -> R.string.sort_field_added
    RecordSortField.RATING -> R.string.sort_field_rating
}
