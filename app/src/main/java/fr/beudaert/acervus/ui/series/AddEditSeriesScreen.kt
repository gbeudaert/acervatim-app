package fr.beudaert.acervus.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.beudaert.acervus.R
import fr.beudaert.acervus.ui.components.CoverPicker
import kotlinx.coroutines.launch

@Composable
fun AddEditSeriesScreen(
    onSaved: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: AddEditSeriesViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val duplicateMessage = stringResource(R.string.duplicate_series_message)

    LaunchedEffect(event) {
        when (val e = event) {
            is AddSeriesEvent.Saved -> {
                viewModel.onEventConsumed()
                onSaved(e.seriesId)
            }
            AddSeriesEvent.NotFound -> {
                viewModel.onEventConsumed()
                onCancel()
            }
            is AddSeriesEvent.DuplicateExists -> {
                viewModel.onEventConsumed()
                scope.launch { snackbarHostState.showSnackbar(duplicateMessage) }
            }
            null -> Unit
        }
    }

    AddEditSeriesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onTitleChange = viewModel::onTitleChange,
        onAuthorChange = viewModel::onAuthorChange,
        onPublicationStatusChange = viewModel::onPublicationStatusChange,
        onTotalVolumesChange = viewModel::onTotalVolumesChange,
        onSynopsisChange = viewModel::onSynopsisChange,
        onCoverPicked = viewModel::onCoverPicked,
        onCoverRemoved = viewModel::onCoverRemoved,
        onSave = viewModel::save,
        onCancel = onCancel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditSeriesContent(
    state: AddSeriesFormState,
    snackbarHostState: SnackbarHostState,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onPublicationStatusChange: (String) -> Unit,
    onTotalVolumesChange: (String) -> Unit,
    onSynopsisChange: (String) -> Unit,
    onCoverPicked: (String) -> Unit,
    onCoverRemoved: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val titleRes = if (state.isEditing) R.string.series_edit_title else R.string.series_add_title

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Button(onClick = onSave, enabled = state.isValid) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    ) { inner ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.add_record_section_cover),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CoverPicker(
                    previewModel = state.coverPreviewModel,
                    cacheKey = "series-${state.seriesId}",
                    hasCover = state.hasCover,
                    onCoverPicked = onCoverPicked,
                    onCoverRemoved = onCoverRemoved
                )
            }

            HorizontalDivider()

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.series_field_title)) },
                isError = state.titleError,
                supportingText = if (state.titleError) {
                    { Text(stringResource(R.string.field_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.originalAuthor,
                onValueChange = onAuthorChange,
                label = { Text(stringResource(R.string.series_field_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.publicationStatus,
                onValueChange = onPublicationStatusChange,
                label = { Text(stringResource(R.string.series_field_publication_status)) },
                placeholder = { Text(stringResource(R.string.series_field_publication_status_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.totalVolumes,
                onValueChange = onTotalVolumesChange,
                label = { Text(stringResource(R.string.series_field_total_volumes)) },
                placeholder = { Text(stringResource(R.string.series_field_total_volumes_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.synopsis,
                onValueChange = onSynopsisChange,
                label = { Text(stringResource(R.string.series_field_synopsis)) },
                placeholder = { Text(stringResource(R.string.series_field_synopsis_hint)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
