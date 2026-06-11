package fr.beudaert.acervatim.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.beudaert.acervatim.R
import fr.beudaert.acervatim.data.remote.auth.AuthState
import fr.beudaert.acervatim.domain.model.sync.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                    onSync = viewModel::syncNow,
                    onSignOut = viewModel::signOut,
                    onDelete = { showDeleteConfirm = true }
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
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit
) {
    Text(
        text = stringResource(R.string.account_signed_in_as, userId.take(8)),
        style = MaterialTheme.typography.titleMedium
    )
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

@Composable
private fun SyncStatusLine(status: SyncStatus) {
    val text = when (status) {
        SyncStatus.Idle -> stringResource(R.string.sync_status_idle)
        SyncStatus.Running -> stringResource(R.string.sync_status_running)
        is SyncStatus.Success -> stringResource(R.string.sync_status_last, status.at.toString())
        is SyncStatus.Failure -> stringResource(R.string.sync_status_error, status.message)
    }
    val color = if (status is SyncStatus.Failure) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}
