package app.acervatim.ui.sharing.received

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.ReceivedEntry
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.ui.sharing.CenteredProgress
import app.acervatim.ui.sharing.FailedContent
import app.acervatim.ui.sharing.ShareLabelDialog
import app.acervatim.ui.sharing.formatShareDate
import app.acervatim.ui.sharing.messageRes
import app.acervatim.ui.sharing.shareScopeSummary
import app.acervatim.ui.theme.AcervatimTheme
import java.time.Instant

/**
 * Écran membre : rejoindre un partage et consulter ceux qu'on a rejoints.
 *
 * **Séparé de « mes collections », volontairement.** Ces collections n'appartiennent pas à
 * l'utilisateur, ne comptent pas dans ses données, ne sont pas sauvegardées, et disparaîtront si le
 * propriétaire révoque. Les mélanger à ses propres collections ferait croire le contraire.
 *
 * Accessible **sans être premium** : c'est le propriétaire qui paie le stockage.
 */
@Composable
fun ReceivedSharesScreen(
    onBack: () -> Unit,
    onOpenSharedCollection: (collectionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceivedSharesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ReceivedSharesScreenContent(
        state = state,
        onBack = onBack,
        onOpenSharedCollection = onOpenSharedCollection,
        onJoin = viewModel::join,
        onRename = viewModel::rename,
        onRetry = viewModel::refresh,
        onErrorShown = viewModel::consumeError,
        onAlreadyMemberShown = viewModel::consumeAlreadyMember,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceivedSharesScreenContent(
    state: ReceivedSharesUiState,
    onBack: () -> Unit,
    onOpenSharedCollection: (String) -> Unit,
    onJoin: (code: String, label: String?) -> Unit,
    onRename: (shareId: String, label: String) -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    onAlreadyMemberShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var joinVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        val failure = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(failure.messageRes()))
        onErrorShown()
    }
    LaunchedEffect(state.alreadyMember) {
        if (!state.alreadyMember) return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(R.string.received_already_member))
        onAlreadyMemberShown()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.received_title)) },
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
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.received_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { joinVisible = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.received_join_action))
                }

                when (val content = state.content) {
                    ReceivedContent.Loading -> CenteredProgress()
                    ReceivedContent.Failed -> FailedContent(onRetry = onRetry)
                    is ReceivedContent.Shares -> if (content.shares.isEmpty()) {
                        Text(
                            text = stringResource(R.string.received_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        content.shares.forEach { share ->
                            ReceivedShareCard(
                                share = share,
                                busy = state.busy,
                                onOpenCollection = onOpenSharedCollection,
                                onRename = { label -> onRename(share.shareId, label) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (joinVisible) {
        JoinShareDialog(
            busy = state.busy,
            onJoin = { code, label ->
                joinVisible = false
                onJoin(code, label)
            },
            onDismiss = { joinVisible = false }
        )
    }
}

@Composable
private fun ReceivedShareCard(
    share: ReceivedShare,
    busy: Boolean,
    onOpenCollection: (String) -> Unit,
    onRename: (String) -> Unit
) {
    var renaming by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = share.label?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.received_unnamed),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IconButton(onClick = { renaming = true }, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.sharing_rename_action)
                    )
                }
            }
            Text(
                text = stringResource(R.string.received_joined_at, formatShareDate(share.redeemedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()
            share.collections.forEach { entry ->
                SharedCollectionRow(entry = entry, onClick = { onOpenCollection(entry.collectionId) })
            }
        }
    }

    if (renaming) {
        ShareLabelDialog(
            current = share.label,
            hintRes = R.string.received_label_hint,
            onConfirm = {
                renaming = false
                onRename(it)
            },
            onDismiss = { renaming = false }
        )
    }
}

/**
 * Une collection partagée, avec ce qu'elle expose et **combien** on y verra.
 *
 * [ReceivedEntry.itemCount] vient du serveur, déjà filtré par les statuts accordés : ne pas le
 * recalculer côté app, qui n'a pas de quoi. Il rend aussi lisible qu'un partage « wantlist » vide
 * n'est pas cassé — il est simplement vide.
 */
@Composable
private fun SharedCollectionRow(entry: ReceivedEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = entry.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = shareScopeSummary(entry.statuses),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = pluralStringResource(
                R.plurals.received_item_count,
                entry.itemCount,
                entry.itemCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReceivedSharesPreview() {
    val now = Instant.parse("2026-08-22T10:00:00Z")
    AcervatimTheme {
        ReceivedSharesScreenContent(
            state = ReceivedSharesUiState(
                content = ReceivedContent.Shares(
                    listOf(
                        ReceivedShare(
                            shareId = "s1",
                            label = "Wantlist manga de Bob",
                            redeemedAt = now,
                            collections = listOf(
                                ReceivedEntry(
                                    collectionId = "c1",
                                    name = "Mangas",
                                    typeCode = "manga",
                                    statuses = setOf(ItemStatus.WISHLIST),
                                    itemCount = 12
                                )
                            )
                        )
                    )
                )
            ),
            onBack = {},
            onOpenSharedCollection = {},
            onJoin = { _, _ -> },
            onRename = { _, _ -> },
            onRetry = {},
            onErrorShown = {},
            onAlreadyMemberShown = {}
        )
    }
}
