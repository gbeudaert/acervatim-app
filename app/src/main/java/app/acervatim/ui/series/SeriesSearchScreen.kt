package app.acervatim.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.model.SeriesEditionOption
import app.acervatim.domain.usecase.ImportResult
import app.acervatim.ui.components.RecordCover

/**
 * Recherche d'une série manga par titre, pour l'ajouter **sans en posséder aucun tome** (wishlist).
 * Un seul écran en trois étapes ([SeriesSearchStage]) : la série, puis son édition française, puis
 * l'import. La difficulté n'est pas la recherche mais les deux **sélections** : chaque ligne porte
 * ce qui la distingue de sa voisine (auteurs, année, statut pour une série ; éditeur, mention,
 * nombre de tomes pour une édition).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesSearchScreen(
    onBack: () -> Unit,
    onConnectSource: () -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    onCreateManually: (title: String, author: String?) -> Unit,
    viewModel: SeriesSearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        when (val current = event) {
            is SeriesSearchEvent.CreateManually -> {
                viewModel.onEventConsumed()
                onCreateManually(current.title, current.author)
            }
            null -> Unit
        }
    }

    // Le retour système suit les étapes : des éditions on revient aux séries, pendant l'import on
    // reste (quitter interromprait l'écriture des tomes en base).
    BackHandler(enabled = state.stage != SeriesSearchStage.SEARCH && state.result == null) {
        viewModel.onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.stage == SeriesSearchStage.EDITIONS) R.string.series_search_editions_title
                            else R.string.series_search_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (state.result != null || !viewModel.onBack()) onBack() },
                        enabled = !state.importing
                    ) {
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
            Box(modifier = Modifier.weight(1f)) {
                when (state.stage) {
                    SeriesSearchStage.SEARCH -> SearchStage(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onRetry = viewModel::onRetry,
                        onConnectSource = onConnectSource,
                        onSelect = viewModel::onSelectCandidate
                    )
                    SeriesSearchStage.EDITIONS -> EditionsStage(
                        candidate = state.selected,
                        editions = state.editions,
                        onSelect = viewModel::onSelectEdition,
                        onRetry = viewModel::onRetryEditions,
                        onCreateManually = viewModel::onCreateManually
                    )
                    SeriesSearchStage.IMPORT -> ImportStage(
                        state = state,
                        onOpenSeries = onOpenSeries,
                        onRetryImport = viewModel::onRetryImport,
                        onBackToEditions = { viewModel.onBack() }
                    )
                }
            }
            // Crédit des sources, dû à MangaDex (politique d'usage) et utile à l'utilisateur : il sait
            // d'où vient ce qu'il choisit.
            Text(
                text = stringResource(R.string.series_search_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun SearchStage(
    state: SeriesSearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onConnectSource: () -> Unit,
    onSelect: (SeriesCandidate) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.series_search_field_label)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
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
            SeriesSearchStep.Idle -> MessageBlock(
                title = stringResource(R.string.series_search_hint_title),
                body = stringResource(R.string.series_search_hint_body)
            )
            SeriesSearchStep.Searching -> Loading()
            SeriesSearchStep.NoMatch -> MessageBlock(
                title = stringResource(R.string.series_search_no_match_title),
                body = stringResource(R.string.series_search_no_match_body, state.query.trim())
            )
            SeriesSearchStep.NotConnected -> MessageBlock(
                title = stringResource(R.string.external_search_not_connected_title),
                body = stringResource(R.string.external_search_not_connected_body)
            )
            is SeriesSearchStep.Failed -> FailureBlock(
                failure = step.failure,
                onRetry = onRetry,
                onConnectSource = onConnectSource
            )
            is SeriesSearchStep.Results -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(step.candidates, key = { "${it.source}:${it.sourceId}" }) { candidate ->
                    CandidateRow(candidate = candidate, onSelect = { onSelect(candidate) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Une série candidate. Le titre français d'abord quand il existe (c'est ce que l'utilisateur
 * reconnaît), le romaji en sous-titre (c'est ce qui départage deux traductions), puis les repères
 * qui écartent un homonyme : auteurs, année, statut et nombre de tomes en VO.
 */
@Composable
private fun CandidateRow(candidate: SeriesCandidate, onSelect: () -> Unit) {
    val displayTitle = candidate.titleFr ?: candidate.title
    val subtitle = candidate.titleRomaji?.takeIf { !it.equals(displayTitle, ignoreCase = true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RecordCover(
            coverPath = null,
            coverUrl = candidate.coverUrl,
            contentDescription = null,
            cornerRadius = 4.dp,
            modifier = Modifier.size(width = 48.dp, height = 68.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = candidate.authors.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(R.string.series_search_unknown_author),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            candidate.facts().takeIf { it.isNotEmpty() }?.let { facts ->
                Text(
                    text = facts,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Année · statut · nombre de tomes en VO — chacun omis s'il est inconnu. */
@Composable
private fun SeriesCandidate.facts(): String {
    val parts = buildList {
        year?.let { add(it.toString()) }
        statusLabel(status)?.let { add(stringResource(it)) }
        volumeCount?.takeIf { it > 0 }?.let {
            add(pluralStringResource(R.plurals.series_search_original_volumes, it, it))
        }
    }
    return parts.joinToString(" · ")
}

/** Statut MangaDex → libellé ; une valeur inconnue (repli MAL…) est omise plutôt que mal traduite. */
private fun statusLabel(status: String?): Int? = when (status) {
    "ongoing" -> R.string.series_search_status_ongoing
    "completed" -> R.string.series_search_status_completed
    "hiatus" -> R.string.series_search_status_hiatus
    "cancelled" -> R.string.series_search_status_cancelled
    else -> null
}

@Composable
private fun EditionsStage(
    candidate: SeriesCandidate?,
    editions: EditionsStep,
    onSelect: (SeriesEditionOption) -> Unit,
    onRetry: () -> Unit,
    onCreateManually: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        candidate?.let {
            Text(
                text = it.titleFr ?: it.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        when (editions) {
            EditionsStep.Loading -> Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.series_search_editions_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            EditionsStep.Empty -> MessageBlock(
                title = stringResource(R.string.series_search_editions_empty_title),
                body = stringResource(R.string.series_search_editions_empty_body),
                action = {
                    OutlinedButton(onClick = onCreateManually) {
                        Text(stringResource(R.string.series_search_create_manually))
                    }
                }
            )
            is EditionsStep.Failed -> FailureBlock(
                failure = editions.failure,
                onRetry = onRetry,
                // Les éditions viennent de la BnF, sans jeton : un refus de jeton n'a rien à router.
                onConnectSource = onRetry
            )
            is EditionsStep.Loaded -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    editions.options,
                    key = { "${it.seriesTitleFr}|${it.publisher.orEmpty()}|${it.edition.orEmpty()}" }
                ) { option ->
                    EditionRow(option = option, onSelect = { onSelect(option) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Une édition française. Ce qui la distingue d'une autre édition de la même œuvre est sur la ligne :
 * éditeur et mention (« Pika · Éd. colossale »), nombre de tomes, années, « en cours », et le fait
 * qu'un tome en regroupe plusieurs de la standard.
 */
@Composable
private fun EditionRow(option: SeriesEditionOption, onSelect: () -> Unit) {
    val editionLabel = option.edition?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.series_search_edition_standard)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val publisherLabel = option.publisher?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.series_search_unknown_publisher)
        Text(
            text = "$publisherLabel · $editionLabel",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = option.facts(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (option.isArtEdition || option.hasSourceVolumeRange) {
            Text(
                text = if (option.hasSourceVolumeRange) {
                    stringResource(R.string.series_search_edition_compiles)
                } else {
                    stringResource(R.string.series_search_edition_art)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Nombre de tomes · années · en cours. */
@Composable
private fun SeriesEditionOption.facts(): String {
    val parts = buildList {
        add(pluralStringResource(R.plurals.series_search_edition_tomes, tomeCount, tomeCount))
        val first = firstYear?.takeIf { it.isNotBlank() }
        val last = lastYear?.takeIf { it.isNotBlank() }
        when {
            first != null && last != null && first != last ->
                add(stringResource(R.string.series_search_edition_years, first, last))
            first != null -> add(first)
            last != null -> add(last)
        }
        if (ongoing) add(stringResource(R.string.series_search_edition_ongoing))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ImportStage(
    state: SeriesSearchUiState,
    onOpenSeries: (String) -> Unit,
    onRetryImport: () -> Unit,
    onBackToEditions: () -> Unit
) {
    val title = state.chosenEdition?.seriesTitleFr ?: state.selected?.let { it.titleFr ?: it.title }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val result = state.result
        if (result != null) {
            ImportResultContent(result = result, onOpenSeries = onOpenSeries)
        } else if (state.importFailed) {
            // Échec franc (écriture, réseau) : rien n'est en vol, l'utilisateur garde la main.
            MessageBlock(
                title = stringResource(R.string.series_search_import_failed_title),
                body = stringResource(R.string.series_search_import_failed_body),
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetryImport, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.external_search_retry))
                        }
                        OutlinedButton(onClick = onBackToEditions, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.series_search_import_back_to_editions))
                        }
                    }
                }
            )
        } else {
            SeriesImportProgress(
                title = title,
                phase = state.importPhase ?: ScanImportPhase.LAUNCHING,
                progress = state.progress
            )
        }
    }
}

@Composable
private fun ImportResultContent(result: ImportResult, onOpenSeries: (String) -> Unit) {
    Text(
        text = pluralStringResource(
            R.plurals.series_search_import_result,
            result.createdVolumes,
            result.createdVolumes,
            result.skippedExisting
        ),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    when {
        result.degradedFullImport -> Text(
            text = stringResource(R.string.series_search_import_degraded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        result.partial -> Text(
            text = stringResource(R.string.series_search_import_ongoing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onOpenSeries(result.seriesId) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.scan_series_import_open))
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
    }
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
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
 * Échec traduit en message actionnable, mêmes messages que la recherche de disques. Le refus de
 * jeton n'arrive ici que si MangaDex est en panne et que le serveur a replié sur MAL : il se lève en
 * reliant une source, comme pour les disques.
 */
@Composable
private fun FailureBlock(
    failure: ExternalSearchFailure,
    onRetry: () -> Unit,
    onConnectSource: () -> Unit
) {
    val retry: @Composable () -> Unit = {
        Button(onClick = onRetry) { Text(stringResource(R.string.external_search_retry)) }
    }
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
            action = retry
        )
        ExternalSearchFailure.RATE_LIMITED -> MessageBlock(
            title = stringResource(R.string.external_search_rate_limited_title),
            body = stringResource(R.string.external_search_rate_limited_body),
            action = retry
        )
        ExternalSearchFailure.UNKNOWN -> MessageBlock(
            title = stringResource(R.string.external_search_unknown_title),
            body = stringResource(R.string.external_search_unknown_body),
            action = retry
        )
    }
}
