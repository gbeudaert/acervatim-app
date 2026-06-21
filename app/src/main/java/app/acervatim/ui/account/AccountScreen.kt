package app.acervatim.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.model.account.PremiumSource
import app.acervatim.domain.model.account.QuotaBucket
import app.acervatim.domain.model.sync.SyncStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val redeemState by viewModel.redeemState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val auth = state.authState) {
                is AuthState.SignedIn -> SignedInContent(
                    userId = auth.userId,
                    syncStatus = state.syncStatus,
                    accountInfo = state.accountInfo,
                    redeemState = redeemState,
                    onSync = viewModel::syncNow,
                    onSignOut = viewModel::signOut,
                    onDelete = { showDeleteConfirm = true },
                    onRedeem = viewModel::redeemInvitation
                )

                AuthState.SignedOut -> SignedOutContent(
                    expired = false,
                    error = state.signInError,
                    onSignIn = { viewModel.signIn(context) }
                )

                AuthState.SessionExpired -> SignedOutContent(
                    expired = true,
                    error = state.signInError,
                    onSignIn = { viewModel.signIn(context) }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.account_delete_confirm_title)) },
            text = { Text(stringResource(R.string.account_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAccount()
                }) { Text(stringResource(R.string.account_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SignedOutContent(
    expired: Boolean,
    error: SignInError?,
    onSignIn: () -> Unit
) {
    Text(
        text = stringResource(R.string.account_signed_out_title),
        style = MaterialTheme.typography.titleLarge
    )
    Text(
        text = stringResource(R.string.account_signed_out_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (expired) {
        Text(
            text = stringResource(R.string.account_session_expired),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    when (error) {
        SignInError.CANCELLED -> Text(
            text = stringResource(R.string.account_sign_in_cancelled),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        SignInError.FAILED -> Text(
            text = stringResource(R.string.account_sign_in_error, ""),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        null -> Unit
    }
    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_sign_in))
    }
}

@Composable
private fun SignedInContent(
    userId: String,
    syncStatus: SyncStatus,
    accountInfo: AccountInfo,
    redeemState: RedeemUiState,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onRedeem: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.account_signed_in_as, userId.take(8)),
        style = MaterialTheme.typography.titleMedium
    )
    PlanCard(accountInfo)
    if (syncStatus is SyncStatus.QuotaReached) {
        QuotaReachedBanner(skipped = syncStatus.skipped)
    }
    SyncStatusLine(syncStatus)
    Button(
        onClick = onSync,
        enabled = syncStatus != SyncStatus.Running,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (syncStatus == SyncStatus.Running) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        }
        Text(stringResource(R.string.sync_now))
    }
    val isPremium = (accountInfo as? AccountInfo.Known)?.profile?.premium?.isPremium == true
    if (!isPremium) {
        RedeemSection(redeemState = redeemState, onRedeem = onRedeem)
    }
    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_sign_out))
    }
    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.account_delete),
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** Carte plan + quotas. Backend muet ⇒ texte neutre, jamais de fonctionnalité retirée. */
@Composable
private fun PlanCard(accountInfo: AccountInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (accountInfo) {
                is AccountInfo.Known -> {
                    val premium = accountInfo.profile.premium
                    Text(
                        text = stringResource(
                            if (premium.isPremium) R.string.account_plan_premium else R.string.account_plan_free
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (premium.isPremium) {
                        val sourceRes = when (premium.source) {
                            PremiumSource.GRANT -> R.string.account_premium_source_grant
                            PremiumSource.SUBSCRIPTION -> R.string.account_premium_source_subscription
                            PremiumSource.NONE, PremiumSource.UNKNOWN -> null
                        }
                        sourceRes?.let {
                            Text(
                                text = stringResource(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        premium.expiresAt?.let { expires ->
                            Text(
                                text = stringResource(R.string.account_premium_expires, formatDate(expires)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    accountInfo.quota?.let { quota ->
                        Text(
                            text = stringResource(R.string.account_quota_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        QuotaRow(stringResource(R.string.account_quota_collections), quota.collections)
                        QuotaRow(stringResource(R.string.account_quota_items), quota.items)
                    }
                }

                AccountInfo.Unknown -> Text(
                    text = stringResource(R.string.account_info_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AccountInfo.SignedOut -> Unit
            }
        }
    }
}

@Composable
private fun QuotaRow(label: String, bucket: QuotaBucket) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (bucket.unlimited) {
                    stringResource(R.string.account_quota_unlimited)
                } else {
                    stringResource(R.string.account_quota_value, bucket.used, requireNotNull(bucket.max))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (bucket.reached) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!bucket.unlimited) {
            LinearProgressIndicator(
                progress = { bucket.ratio ?: 0f },
                modifier = Modifier.fillMaxWidth(),
                color = if (bucket.reached) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QuotaReachedBanner(skipped: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = stringResource(R.string.sync_status_quota_reached, skipped),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun RedeemSection(
    redeemState: RedeemUiState,
    onRedeem: (String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.account_redeem_title),
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.account_redeem_hint)) },
            singleLine = true,
            enabled = redeemState != RedeemUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        )
        when (redeemState) {
            RedeemUiState.Success -> Text(
                text = stringResource(R.string.account_redeem_success),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            RedeemUiState.AlreadyRedeemed -> Text(
                text = stringResource(R.string.account_redeem_already),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is RedeemUiState.Error -> Text(
                text = stringResource(R.string.account_redeem_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            RedeemUiState.Idle, RedeemUiState.Loading -> Unit
        }
        OutlinedButton(
            onClick = { onRedeem(code) },
            enabled = code.isNotBlank() && redeemState != RedeemUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.account_redeem_action))
        }
    }
}

@Composable
private fun SyncStatusLine(status: SyncStatus) {
    val text = when (status) {
        SyncStatus.Idle -> stringResource(R.string.sync_status_idle)
        SyncStatus.Running -> stringResource(R.string.sync_status_running)
        is SyncStatus.Success -> stringResource(R.string.sync_status_last, status.at.toString())
        is SyncStatus.QuotaReached -> stringResource(R.string.sync_status_last, status.at.toString())
        is SyncStatus.Failure -> stringResource(R.string.sync_status_error, status.message)
    }
    val color = if (status is SyncStatus.Failure) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}

private fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(instant)
