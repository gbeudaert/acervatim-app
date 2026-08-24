package app.acervatim.ui.sharing

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
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.ShareEntry
import app.acervatim.domain.model.sharing.ShareMember
import app.acervatim.domain.model.sharing.ShareScopePreset
import app.acervatim.domain.model.sharing.ShareState
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.model.sharing.canonical
import app.acervatim.ui.theme.AcervatimTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Écran propriétaire du partage : créer un code, voir ce qu'il expose et qui a rejoint, renommer,
 * révoquer.
 *
 * Il n'est **plus ancré sur une collection** : un partage en réunit plusieurs sous un seul code
 * (sprint backend S5). Il vit donc au niveau du compte, et l'entrée du drawer parle des « partages »
 * et non de « cette collection ».
 *
 * Le code d'un partage n'apparaît qu'ici, une fois, dans [ShareCodeDialog] — il n'est ni sauvegardé
 * ni réaffichable. Tout l'écran est construit autour de ça : proposer la transmission immédiate
 * plutôt que laisser croire qu'on le retrouvera plus tard.
 */
@Composable
fun SharingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val created by viewModel.createdShare.collectAsStateWithLifecycle()
    var createSheetVisible by remember { mutableStateOf(false) }

    SharingScreenContent(
        state = state,
        onBack = onBack,
        onOpenCreate = { createSheetVisible = true },
        onRetry = viewModel::retry,
        onRename = viewModel::renameShare,
        onRevokeShare = viewModel::revokeShare,
        onRevokeMember = viewModel::revokeMember,
        onErrorShown = viewModel::consumeError,
        modifier = modifier
    )

    if (createSheetVisible) {
        CreateShareSheet(
            sharable = state.sharable,
            busy = state.busy,
            onCreate = { entries, label, maxUses, expiresAt ->
                createSheetVisible = false
                viewModel.createShare(entries, label, maxUses, expiresAt)
            },
            onDismiss = { createSheetVisible = false }
        )
    }

    created?.let { share ->
        ShareCodeDialog(created = share, onDismiss = viewModel::consumeCreatedShare)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharingScreenContent(
    state: SharingUiState,
    onBack: () -> Unit,
    onOpenCreate: () -> Unit,
    onRetry: () -> Unit,
    onRename: (shareId: String, label: String) -> Unit,
    onRevokeShare: (String) -> Unit,
    onRevokeMember: (shareId: String, memberUserId: String) -> Unit,
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
                title = { Text(stringResource(R.string.sharing_title)) },
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
                when (val content = state.content) {
                    SharingContent.Loading -> CenteredProgress()
                    SharingContent.NotSynced -> NoticeCard(
                        titleRes = R.string.sharing_not_synced_title,
                        messageRes = R.string.sharing_not_synced_message
                    )
                    SharingContent.Failed -> FailedContent(onRetry = onRetry)
                    is SharingContent.Shares -> SharesContent(
                        shares = content.shares,
                        canCreate = state.canCreate,
                        busy = state.busy,
                        onOpenCreate = onOpenCreate,
                        onRename = onRename,
                        onRevokeShare = onRevokeShare,
                        onRevokeMember = onRevokeMember
                    )
                }
            }
        }
    }
}

@Composable
private fun SharesContent(
    shares: List<CollectionShare>,
    canCreate: Boolean,
    busy: Boolean,
    onOpenCreate: () -> Unit,
    onRename: (shareId: String, label: String) -> Unit,
    onRevokeShare: (String) -> Unit,
    onRevokeMember: (shareId: String, memberUserId: String) -> Unit
) {
    Text(
        text = stringResource(R.string.sharing_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (canCreate) {
        Button(
            onClick = onOpenCreate,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.sharing_create_action))
        }
    } else {
        // Premium perdu (ou jamais eu) : plus de création, mais renommage et révocation restent
        // ouverts — sinon on retiendrait en otage des partages déjà émis.
        NoticeCard(
            titleRes = R.string.sharing_premium_title,
            messageRes = R.string.sharing_premium_message
        )
    }

    if (shares.isEmpty()) {
        Text(
            text = stringResource(R.string.sharing_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Text(
        text = stringResource(R.string.sharing_active_shares),
        style = MaterialTheme.typography.titleSmall
    )
    val now = remember { Instant.now() }
    shares.forEach { share ->
        ShareCard(
            share = share,
            now = now,
            busy = busy,
            onRename = { label -> onRename(share.id, label) },
            onRevokeShare = { onRevokeShare(share.id) },
            onRevokeMember = { memberId -> onRevokeMember(share.id, memberId) }
        )
    }
}

@Composable
private fun ShareCard(
    share: CollectionShare,
    now: Instant,
    busy: Boolean,
    onRename: (String) -> Unit,
    onRevokeShare: () -> Unit,
    onRevokeMember: (String) -> Unit
) {
    var confirmRevoke by remember { mutableStateOf(false) }
    var memberToRevoke by remember { mutableStateOf<String?>(null) }
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
                    // Un partage sans libellé n'est pas anonyme pour rien : le champ est optionnel.
                    // On donne un titre neutre plutôt qu'une ligne vide.
                    text = share.label?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.sharing_unnamed),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShareStateChip(state = share.stateAt(now))
                    IconButton(onClick = { renaming = true }, enabled = !busy) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.sharing_rename_action)
                        )
                    }
                }
            }

            ShareEntriesBlock(entries = share.collections)

            Text(
                text = stringResource(R.string.sharing_created_at, formatShareDate(share.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            share.expiresAt?.let {
                Text(
                    text = stringResource(R.string.sharing_expires_at, formatShareDate(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.sharing_uses, share.usedCount, share.maxUses),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()
            if (share.members.isEmpty()) {
                Text(
                    text = stringResource(R.string.sharing_no_member),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.sharing_members_count,
                        share.members.size,
                        share.members.size
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                share.members.forEach { member ->
                    MemberRow(
                        member = member,
                        busy = busy,
                        onRevoke = { memberToRevoke = member.memberUserId }
                    )
                }
            }

            OutlinedButton(
                onClick = { confirmRevoke = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.sharing_revoke),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (renaming) {
        ShareLabelDialog(
            current = share.label,
            hintRes = R.string.sharing_label_hint,
            onConfirm = {
                renaming = false
                onRename(it)
            },
            onDismiss = { renaming = false }
        )
    }
    if (confirmRevoke) {
        ConfirmDialog(
            titleRes = R.string.sharing_revoke_confirm_title,
            messageRes = R.string.sharing_revoke_confirm_message,
            onConfirm = {
                confirmRevoke = false
                onRevokeShare()
            },
            onDismiss = { confirmRevoke = false }
        )
    }
    memberToRevoke?.let { memberId ->
        ConfirmDialog(
            titleRes = R.string.sharing_revoke_member_confirm_title,
            messageRes = R.string.sharing_revoke_member_confirm_message,
            onConfirm = {
                memberToRevoke = null
                onRevokeMember(memberId)
            },
            onDismiss = { memberToRevoke = null }
        )
    }
}

/**
 * Ce que le partage expose, collection par collection.
 *
 * C'est le cœur de l'écran depuis S5 : sans ce bloc, deux codes portant le même libellé seraient
 * indiscernables alors qu'ils n'exposent pas les mêmes données. Les statuts sont **non modifiables**
 * — re-scoper un partage vivant changerait sans le dire ce que ses membres voient déjà ; il faut
 * révoquer et réémettre.
 */
@Composable
private fun ShareEntriesBlock(entries: List<ShareEntry>) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry ->
            Text(text = entry.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = shareScopeSummary(entry.statuses),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Un membre n'est qu'un UUID : les comptes sont anonymes en base, il n'y a ni nom ni e-mail à
 * afficher. On en montre le début, assez pour distinguer deux lignes, et rien de plus.
 */
@Composable
private fun MemberRow(
    member: ShareMember,
    busy: Boolean,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = stringResource(R.string.sharing_member_label, member.memberUserId.take(8)),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.sharing_member_since, formatShareDate(member.redeemedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRevoke, enabled = !busy) {
            Icon(
                imageVector = Icons.Default.PersonRemove,
                contentDescription = stringResource(R.string.sharing_revoke_member),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ShareStateChip(state: ShareState) {
    val labelRes = when (state) {
        ShareState.ACTIVE -> R.string.sharing_state_active
        ShareState.EXPIRED -> R.string.sharing_state_expired
        ShareState.EXHAUSTED -> R.string.sharing_state_exhausted
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(labelRes)) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = if (state == ShareState.ACTIVE) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    )
}

@Composable
private fun ConfirmDialog(
    titleRes: Int,
    messageRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.sharing_revoke_action),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
internal fun NoticeCard(titleRes: Int, messageRes: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
internal fun FailedContent(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.sharing_load_error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sharing_retry))
    }
}

@Composable
internal fun CenteredProgress() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Résumé de ce qu'un partage expose d'une collection : « Toute la collection » quand l'ensemble est
 * complet, sinon la liste des statuts.
 *
 * Les anciennes portées survivent ainsi comme vocabulaire d'**affichage** : elles se lisent mieux
 * qu'une énumération, mais elles ne contraignent plus le choix.
 *
 * Les libellés sont résolus dans une boucle plutôt que dans un `joinToString { }` : le `transform`
 * de `joinToString` est noinline, son corps n'est donc pas un contexte @Composable.
 */
@Composable
internal fun shareScopeSummary(statuses: Set<ItemStatus>): String {
    if (statuses.isEmpty()) return stringResource(R.string.sharing_statuses_none)
    if (ShareScopePreset.matching(statuses) == ShareScopePreset.ALL) {
        return stringResource(R.string.sharing_scope_all)
    }
    val labels = mutableListOf<String>()
    for (status in statuses.canonical()) labels += stringResource(status.shareLabelRes())
    return labels.joinToString(", ")
}

/**
 * Libellé d'un statut **du point de vue du membre** : « ce que je verrai », pas le nom technique.
 * C'est la formulation qui évite d'exposer une wantlist par erreur.
 */
internal fun ItemStatus.shareLabelRes(): Int = when (this) {
    ItemStatus.OWNED -> R.string.sharing_status_owned
    ItemStatus.WISHLIST -> R.string.sharing_status_wishlist
    ItemStatus.IGNORED -> R.string.sharing_status_ignored
}

internal fun SharingFailure.messageRes(): Int = when (this) {
    SharingFailure.PREMIUM_REQUIRED -> R.string.sharing_error_premium
    SharingFailure.CODE_INVALID -> R.string.sharing_error_code_invalid
    SharingFailure.OWN_SHARE -> R.string.sharing_error_own_share
    SharingFailure.TOO_MANY_ATTEMPTS -> R.string.sharing_error_too_many
    SharingFailure.GONE -> R.string.sharing_error_gone
    SharingFailure.COLLECTION_GONE -> R.string.sharing_error_collection_gone
    SharingFailure.NETWORK -> R.string.sharing_error_network
    SharingFailure.UNKNOWN -> R.string.sharing_error_unknown
}

internal fun formatShareDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(instant)

@Preview(showBackground = true)
@Composable
private fun SharingScreenPreview() {
    val now = Instant.parse("2026-08-22T10:00:00Z")
    AcervatimTheme {
        SharingScreenContent(
            state = SharingUiState(
                content = SharingContent.Shares(
                    listOf(
                        CollectionShare(
                            id = "s1",
                            label = "Wantlist manga avec Alice",
                            maxUses = 3,
                            usedCount = 1,
                            expiresAt = null,
                            createdAt = now,
                            collections = listOf(
                                ShareEntry("c1", "Mangas", "manga", setOf(ItemStatus.WISHLIST)),
                                ShareEntry(
                                    "c2",
                                    "Vinyles",
                                    "vinyl",
                                    setOf(ItemStatus.OWNED, ItemStatus.WISHLIST)
                                )
                            ),
                            members = listOf(ShareMember("8f3c1d2e-aaaa-bbbb-cccc-ddddeeeeffff", now))
                        ),
                        CollectionShare(
                            id = "s2",
                            label = null,
                            maxUses = 1,
                            usedCount = 1,
                            expiresAt = now,
                            createdAt = now,
                            collections = listOf(
                                ShareEntry("c2", "Vinyles", "vinyl", ItemStatus.entries.toSet())
                            )
                        )
                    )
                )
            ),
            onBack = {},
            onOpenCreate = {},
            onRetry = {},
            onRename = { _, _ -> },
            onRevokeShare = {},
            onRevokeMember = { _, _ -> },
            onErrorShown = {}
        )
    }
}
