package app.acervatim.ui.sharing.received

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.SharedFact
import app.acervatim.domain.model.sharing.SharedFactKey
import app.acervatim.domain.model.sharing.SharedItemDetail
import androidx.compose.foundation.layout.size
import app.acervatim.ui.components.RecordCover
import app.acervatim.ui.sharing.CenteredProgress
import app.acervatim.ui.sharing.FailedContent
import app.acervatim.ui.sharing.NoticeCard
import app.acervatim.ui.sharing.shareLabelRes
import app.acervatim.ui.theme.AcervatimTheme

/**
 * Détail d'un élément partagé. **Aucune action** : ni édition, ni suppression, ni bascule wishlist,
 * ni note, ni scan vers cette collection. Elles ne sont pas désactivées — elles n'existent pas.
 *
 * `purchasePrice`, la note libre et le commentaire de série sont **absents du JSON** pour un membre
 * (S4 réduit le `userData`). L'écran les traite donc comme inexistants : il n'affiche pas un champ
 * vide, qui ferait croire à une lacune du propriétaire.
 */
@Composable
fun SharedItemDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedItemDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SharedItemDetailContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedItemDetailContent(
    state: SharedItemState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? SharedItemState.Ready)?.item?.title
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state) {
                SharedItemState.Loading -> CenteredProgress()
                SharedItemState.Failed -> FailedContent(onRetry = onRetry)
                SharedItemState.Gone -> NoticeCard(
                    titleRes = R.string.shared_gone_title,
                    messageRes = R.string.shared_gone_message
                )
                SharedItemState.Suspended -> NoticeCard(
                    titleRes = R.string.shared_suspended_title,
                    messageRes = R.string.shared_suspended_message
                )
                is SharedItemState.Ready -> SharedItemBody(state.item)
            }
        }
    }
}

@Composable
private fun SharedItemBody(item: SharedItemDetail) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Le composant de jaquette commun, alimente par l'URL provider : une collection
        // partagee n'a par definition aucun fichier local.
        RecordCover(
            coverPath = null,
            coverUrl = item.coverUrl,
            contentDescription = item.title,
            cornerRadius = 8.dp,
            placeholderIconSize = 48.dp,
            modifier = Modifier.size(120.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.title ?: stringResource(R.string.shared_untitled),
                style = MaterialTheme.typography.titleLarge
            )
            item.status?.let {
                Text(
                    text = stringResource(it.shareLabelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (item.facts.isNotEmpty()) {
        HorizontalDivider()
        item.facts.forEach { FactRow(it) }
    }

    // `rating` et `lastPlayedAt` sont les seules données perso qui descendent jusqu'au membre.
    // Absents ⇒ rien affiché : la ligne ne doit pas exister plutôt qu'exister vide.
    if (item.rating != null || item.lastPlayedAt != null) {
        HorizontalDivider()
        item.rating?.let {
            LabelledValue(
                label = stringResource(R.string.shared_fact_rating),
                value = it.toString()
            )
        }
        item.lastPlayedAt?.let {
            LabelledValue(label = stringResource(R.string.shared_fact_last_played), value = it)
        }
    }
}

@Composable
private fun FactRow(fact: SharedFact) {
    LabelledValue(label = stringResource(fact.key.labelRes()), value = fact.value)
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun SharedFactKey.labelRes(): Int = when (this) {
    SharedFactKey.CREATORS -> R.string.shared_fact_creators
    SharedFactKey.GENRE -> R.string.shared_fact_genre
    SharedFactKey.LABEL -> R.string.shared_fact_label
    SharedFactKey.FORMAT -> R.string.shared_fact_format
    SharedFactKey.RELEASE_DATE -> R.string.shared_fact_release_date
    SharedFactKey.VOLUME -> R.string.shared_fact_volume
}

@Preview(showBackground = true)
@Composable
private fun SharedItemDetailPreview() {
    AcervatimTheme {
        SharedItemDetailContent(
            state = SharedItemState.Ready(
                SharedItemDetail(
                    id = "i1",
                    title = "Kind of Blue",
                    coverUrl = null,
                    volume = null,
                    status = ItemStatus.OWNED,
                    rating = 9,
                    lastPlayedAt = null,
                    facts = listOf(
                        SharedFact(SharedFactKey.CREATORS, "Miles Davis"),
                        SharedFact(SharedFactKey.GENRE, "Jazz"),
                        SharedFact(SharedFactKey.RELEASE_DATE, "1959-08-17")
                    )
                )
            ),
            onBack = {},
            onRetry = {}
        )
    }
}
