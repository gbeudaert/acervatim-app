package app.acervatim.ui.sharing.received

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.repository.SharedSeries
import app.acervatim.ui.list.LoadingState
import app.acervatim.ui.series.SeriesDetailBody
import app.acervatim.ui.sharing.FailedContent
import app.acervatim.ui.sharing.NoticeCard
import app.acervatim.ui.theme.AcervatimTheme
import java.time.Instant

/**
 * Détail d'une série partagée.
 *
 * **Même rendu que ses propres séries** : le corps est [SeriesDetailBody], le composant partagé —
 * en-tête dépliable avec jaquette zoomable, synopsis, grille de tomes.
 *
 * Deux différences, et deux seulement : le bandeau **lecture seule** hérité de l'écran de collection,
 * et **aucune action**. Ni suppression de série, ni édition de tome, ni bascule wishlist, ni
 * rafraîchissement de métadonnées, ni mode sélection — elles ne sont pas désactivées, elles ne sont
 * pas construites. Les paramètres par défaut de [SeriesDetailBody] suffisent à neutraliser la
 * sélection.
 *
 * Un tome ouvre le détail d'élément partagé, comme depuis la vue plate.
 */
@Composable
fun SharedSeriesDetailScreen(
    onBack: () -> Unit,
    onOpenVolume: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedSeriesDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SharedSeriesDetailContent(
        state = state,
        onBack = onBack,
        onOpenVolume = onOpenVolume,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedSeriesDetailContent(
    state: SharedSeriesState,
    onBack: () -> Unit,
    onOpenVolume: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? SharedSeriesState.Ready)?.series?.series?.title
                            ?: stringResource(R.string.shared_untitled),
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
            // Le marqueur reste visible en profondeur : quitter la liste ne doit pas faire oublier
            // qu'on est chez quelqu'un d'autre, et que rien n'y est modifiable.
            SharedReadOnlyBanner()

            when (state) {
                SharedSeriesState.Loading -> LoadingState()
                SharedSeriesState.Failed -> Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FailedContent(onRetry = onRetry)
                }
                SharedSeriesState.Gone -> Column(modifier = Modifier.padding(24.dp)) {
                    NoticeCard(
                        titleRes = R.string.shared_gone_title,
                        messageRes = R.string.shared_gone_message
                    )
                }
                SharedSeriesState.Suspended -> Column(
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
                is SharedSeriesState.Ready -> SeriesDetailBody(
                    series = state.series.series,
                    volumes = state.series.volumes,
                    onOpenVolume = onOpenVolume
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SharedSeriesDetailPreview() {
    val now = Instant.parse("2026-08-23T10:00:00Z")
    AcervatimTheme {
        SharedSeriesDetailContent(
            state = SharedSeriesState.Ready(
                SharedSeries(
                    series = Series(
                        id = "n1",
                        collectionType = CollectionType.MANGA,
                        title = "Berserk",
                        originalAuthor = "Kentaro Miura",
                        totalVolumes = 41,
                        createdAt = now,
                        updatedAt = now
                    ),
                    volumes = listOf(
                        Record(
                            id = "v1",
                            collectionType = CollectionType.MANGA,
                            seriesId = "n1",
                            volumeNumber = 1,
                            artists = emptyList(),
                            title = "Berserk T.1",
                            status = ItemStatus.OWNED,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                )
            ),
            onBack = {},
            onOpenVolume = {},
            onRetry = {}
        )
    }
}
