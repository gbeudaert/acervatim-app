package app.acervatim.ui.sharing.received

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.ListMode
import app.acervatim.ui.list.CollectionSection
import app.acervatim.ui.list.CollectionUiState
import app.acervatim.ui.list.ContentList
import app.acervatim.ui.list.EmptyState
import app.acervatim.ui.list.LoadingState
import app.acervatim.ui.list.SerialContentList
import app.acervatim.ui.list.TileContentBody
import app.acervatim.ui.sharing.FailedContent
import app.acervatim.ui.sharing.NoticeCard
import app.acervatim.ui.sharing.messageRes
import app.acervatim.ui.theme.AcervatimTheme

/**
 * Consultation d'une collection partagée.
 *
 * **Même rendu que ses propres collections** : le corps de liste est le composant partagé
 * ([ContentList] / [SerialContentList]), alimenté par le même [CollectionUiState]. Sections par
 * lettre, bascule plate/série, recherche, états vides — et le tri quand il arrivera — sont hérités,
 * pas réécrits.
 *
 * Trois différences, et trois seulement :
 * 1. un en-tête portant le **nom de la collection** (on n'est plus chez soi) ;
 * 2. un marqueur **lecture seule** permanent, avec la portée accordée ;
 * 3. **aucun bouton d'ajout**, ni tiroir, ni mode sélection.
 *
 * Les actions d'édition ne sont pas *masquées* : elles ne sont pas construites. Le backend refuserait
 * toute mutation d'un membre (`403` au guard), mais une UI qui n'offre rien à cliquer est la première
 * ligne — un `403` sur une vue partagée serait un bug d'UI, pas un message à afficher.
 */
@Composable
fun SharedCollectionScreen(
    onBack: () -> Unit,
    onOpenItem: (itemId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedCollectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SharedCollectionScreenContent(
        state = state,
        onBack = onBack,
        onOpenItem = onOpenItem,
        onOpenSeries = onOpenSeries,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::onClearQuery,
        onListModeChange = viewModel::onListModeChange,
        onRetry = viewModel::retry,
        onErrorShown = viewModel::consumeError,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedCollectionScreenContent(
    state: SharedCollectionUiState,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onListModeChange: (ListMode) -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.error) {
        val failure = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(failure.messageRes()))
        onErrorShown()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.collectionName ?: stringResource(R.string.received_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            SharedReadOnlyBanner(statuses = state.statuses)

            if (state.state == SharedCollectionContentState.Ready) {
                SharedSearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery
                )
                if (state.supportsSerial) {
                    ListModeChips(selected = state.listMode, onSelect = onListModeChange)
                }
            }

            when (state.state) {
                SharedCollectionContentState.Loading -> LoadingState()
                SharedCollectionContentState.Failed -> Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FailedContent(onRetry = onRetry)
                }
                SharedCollectionContentState.Gone -> Column(modifier = Modifier.padding(24.dp)) {
                    NoticeCard(
                        titleRes = R.string.shared_gone_title,
                        messageRes = R.string.shared_gone_message
                    )
                }
                SharedCollectionContentState.Suspended -> Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NoticeCard(
                        titleRes = R.string.shared_suspended_title,
                        messageRes = R.string.shared_suspended_message
                    )
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sharing_retry))
                    }
                }
                SharedCollectionContentState.Ready -> when (val c = state.collection) {
                    CollectionUiState.Loading -> LoadingState()
                    is CollectionUiState.Empty -> EmptyState(c)
                    // Aucun paramètre de sélection : le mode sélection n'existe pas ici, et ses
                    // valeurs par défaut suffisent à le désactiver.
                    is CollectionUiState.FlatContent -> ContentList(
                        state = c,
                        onRecordClick = onOpenItem
                    )
                    is CollectionUiState.SerialContent -> SerialContentList(
                        state = c,
                        onSeriesClick = onOpenSeries
                    )
                    is CollectionUiState.TileContent -> TileContentBody(
                        state = c,
                        onTileClick = if (c.serial) onOpenSeries else onOpenItem
                    )
                }
            }
        }
    }
}

/**
 * Champ de recherche simple, pas la `SearchBar` de la collection locale : celle-ci porte le tiroir,
 * les filtres avancés et les réglages, qui n'ont aucun sens sur une collection d'autrui.
 */
@Composable
private fun SharedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.collection_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.collection_search_clear)
                    )
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Bascule plate / série, proposée seulement si le type de collection est hiérarchique. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListModeChips(selected: ListMode, onSelect: (ListMode) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        FilterChip(
            selected = selected == ListMode.SERIAL,
            onClick = { onSelect(ListMode.SERIAL) },
            label = { Text(stringResource(R.string.shared_mode_serial)) }
        )
        FilterChip(
            selected = selected == ListMode.FLAT,
            onClick = { onSelect(ListMode.FLAT) },
            label = { Text(stringResource(R.string.shared_mode_flat)) }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SharedCollectionPreview() {
    AcervatimTheme {
        SharedCollectionScreenContent(
            state = SharedCollectionUiState(
                collectionName = "Vinyles de Bob",
                statuses = setOf(ItemStatus.OWNED),
                state = SharedCollectionContentState.Ready,
                collection = CollectionUiState.FlatContent(
                    activeCollectionType = CollectionType.VINYL,
                    sections = emptyList<CollectionSection>(),
                    ownedCount = 128,
                    wishlistCount = 4,
                    hasActiveFilters = false
                )
            ),
            onBack = {},
            onOpenItem = {},
            onOpenSeries = {},
            onQueryChange = {},
            onClearQuery = {},
            onListModeChange = {},
            onRetry = {},
            onErrorShown = {}
        )
    }
}
