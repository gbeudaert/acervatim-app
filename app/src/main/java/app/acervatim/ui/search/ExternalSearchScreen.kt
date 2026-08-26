package app.acervatim.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.PressingGroup
import app.acervatim.ui.components.RecordCover

/**
 * Recherche d'un disque par titre/artiste — le chemin d'ajout des disques **sans code-barres**.
 *
 * L'écran n'a pas la recherche pour difficulté, mais la **sélection** : un texte renvoie couramment
 * vingt à trente pressages du même album. Les résultats sont donc regroupés par album, et chaque
 * pressage porte sa ligne discriminante (année · label · pays · format · n° de catalogue) — sans
 * elle, deux lignes identiques à l'écran désigneraient deux disques différents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalSearchScreen(
    onBack: () -> Unit,
    onConnectSource: () -> Unit,
    onPressingSelected: (prefillKey: String) -> Unit,
    viewModel: ExternalSearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        when (val current = event) {
            is ExternalSearchEvent.PrefillNewRecord -> {
                viewModel.onEventConsumed()
                onPressingSelected(current.prefillKey)
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.external_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.external_search_field_label)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.external_search_clear)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (val step = state.step) {
                SearchStep.Idle -> MessageBlock(
                    title = stringResource(R.string.external_search_hint_title),
                    body = stringResource(R.string.external_search_hint_body)
                )
                SearchStep.Searching -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                }
                SearchStep.NoMatch -> MessageBlock(
                    title = stringResource(R.string.external_search_no_match_title),
                    body = stringResource(R.string.external_search_no_match_body, state.query.trim())
                )
                SearchStep.NotConnected -> MessageBlock(
                    title = stringResource(R.string.external_search_not_connected_title),
                    body = stringResource(R.string.external_search_not_connected_body)
                )
                is SearchStep.Failed -> FailureBlock(
                    failure = step.failure,
                    onRetry = viewModel::onRetry,
                    onConnectSource = onConnectSource
                )
                is SearchStep.Results -> ResultsList(
                    groups = step.groups,
                    expandedGroups = state.expandedGroups,
                    onToggleGroup = viewModel::onToggleGroup,
                    onSelect = viewModel::onSelectPressing
                )
            }
        }
    }
}

@Composable
private fun ResultsList(
    groups: List<PressingGroup>,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onSelect: (ExternalMatch) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(groups, key = { it.key }) { group ->
            AlbumGroup(
                group = group,
                expanded = group.key in expandedGroups,
                onToggle = { onToggleGroup(group.key) },
                onSelect = onSelect
            )
            HorizontalDivider()
        }
    }
}

/**
 * Un album et ses pressages. Un album qui n'en a qu'un se choisit d'un seul geste ; au-delà, la
 * vignette se déplie — c'est là que se joue la désambiguïsation, il ne faut pas la préempter en
 * retenant un pressage à la place de l'utilisateur.
 */
@Composable
private fun AlbumGroup(
    group: PressingGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (ExternalMatch) -> Unit
) {
    val single = group.pressings.singleOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (single != null) onSelect(single) else onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RecordCover(
            coverPath = null,
            coverUrl = group.coverUrl,
            contentDescription = null,
            cornerRadius = 4.dp,
            modifier = Modifier.size(56.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = group.creators.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(R.string.external_search_unknown_artist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = single?.discriminants()
                    ?: pluralStringResource(
                        R.plurals.external_search_pressings,
                        group.pressings.size,
                        group.pressings.size
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (single == null) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }
    }
    if (single == null && expanded) {
        group.pressings.forEach { pressing ->
            PressingRow(pressing = pressing, onSelect = { onSelect(pressing) })
        }
    }
}

/** Une ligne de pressage : rien d'autre que ce qui le distingue de ses voisins. */
@Composable
private fun PressingRow(pressing: ExternalMatch, onSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 84.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = pressing.discriminants(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.external_search_choose_pressing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Ligne discriminante d'un pressage : année · label · pays · format · n° de catalogue. C'est elle
 * qui rend la liste exploitable — l'année d'abord (le repère le plus lisible), le numéro de
 * catalogue en dernier (le plus décisif quand tout le reste est identique).
 */
@Composable
private fun ExternalMatch.discriminants(): String {
    val parts = buildList {
        add(year?.toString() ?: stringResource(R.string.external_search_pressing_unknown_year))
        label?.takeIf { it.isNotBlank() }?.let(::add)
        country?.takeIf { it.isNotBlank() }?.let(::add)
        format?.let { add(it.label()) }
        catalogNumber?.takeIf { it.isNotBlank() }
            ?.let { add(stringResource(R.string.external_search_catalog_number, it)) }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun MessageBlock(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

/**
 * Échec traduit en message actionnable. Le cas décisif est le refus de jeton : afficher « aucun
 * résultat » y ferait croire à l'utilisateur que son disque n'est pas référencé, alors qu'il n'a
 * simplement pas l'accès à la base.
 */
@Composable
private fun FailureBlock(
    failure: ExternalSearchFailure,
    onRetry: () -> Unit,
    onConnectSource: () -> Unit
) {
    when (failure) {
        ExternalSearchFailure.SOURCE_TOKEN_REQUIRED -> MessageBlock(
            title = stringResource(R.string.external_search_token_title),
            body = stringResource(R.string.external_search_token_body),
            action = {
                Button(onClick = onConnectSource) {
                    Text(stringResource(R.string.external_search_token_action))
                }
            }
        )
        ExternalSearchFailure.NETWORK -> MessageBlock(
            title = stringResource(R.string.external_search_network_title),
            body = stringResource(R.string.external_search_network_body),
            action = { RetryButton(onRetry) }
        )
        ExternalSearchFailure.RATE_LIMITED -> MessageBlock(
            title = stringResource(R.string.external_search_rate_limited_title),
            body = stringResource(R.string.external_search_rate_limited_body),
            action = { RetryButton(onRetry) }
        )
        ExternalSearchFailure.UNKNOWN -> MessageBlock(
            title = stringResource(R.string.external_search_unknown_title),
            body = stringResource(R.string.external_search_unknown_body),
            action = { RetryButton(onRetry) }
        )
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    Button(onClick = onRetry) {
        Text(stringResource(R.string.external_search_retry))
    }
}

@Composable
private fun AcervatimFormat.label(): String = when (this) {
    AcervatimFormat.LP_33 -> stringResource(R.string.format_lp_33)
    AcervatimFormat.SINGLE_45 -> stringResource(R.string.format_single_45)
    AcervatimFormat.SHELLAC_78 -> stringResource(R.string.format_shellac_78)
    AcervatimFormat.OTHER -> stringResource(R.string.format_other)
}
