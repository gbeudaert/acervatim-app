package fr.beudaert.acervus.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.beudaert.acervus.R
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.MangaDemographic
import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.ui.components.CoverPicker
import kotlinx.coroutines.launch

@Composable
fun AddEditMangaScreen(
    onSaved: (String) -> Unit,
    onCancel: () -> Unit,
    onAddSeries: () -> Unit,
    viewModel: AddEditMangaViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val series by viewModel.availableSeries.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val duplicateMessage = stringResource(R.string.duplicate_isbn_message)

    LaunchedEffect(event) {
        when (val e = event) {
            is AddMangaEvent.Saved -> {
                viewModel.onEventConsumed()
                onSaved(e.recordId)
            }
            AddMangaEvent.NotFound -> {
                viewModel.onEventConsumed()
                onCancel()
            }
            is AddMangaEvent.DuplicateIsbn -> {
                viewModel.onEventConsumed()
                scope.launch { snackbarHostState.showSnackbar(duplicateMessage) }
            }
            null -> Unit
        }
    }

    AddEditMangaContent(
        state = state,
        availableSeries = series,
        snackbarHostState = snackbarHostState,
        onTitleChange = viewModel::onTitleChange,
        onSeriesSelected = viewModel::onSeriesSelected,
        onAddSeries = onAddSeries,
        onVolumeNumberChange = viewModel::onVolumeNumberChange,
        onIsbnChange = viewModel::onIsbnChange,
        onPublisherFrChange = viewModel::onPublisherFrChange,
        onOriginalAuthorChange = viewModel::onOriginalAuthorChange,
        onDemographicChange = viewModel::onDemographicChange,
        onPageCountChange = viewModel::onPageCountChange,
        onStatusChange = viewModel::onStatusChange,
        onRatingChange = viewModel::onRatingChange,
        onCoverPicked = viewModel::onCoverPicked,
        onCoverRemoved = viewModel::onCoverRemoved,
        onSave = viewModel::save,
        onCancel = onCancel
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditMangaContent(
    state: AddMangaFormState,
    availableSeries: List<Series>,
    snackbarHostState: SnackbarHostState,
    onTitleChange: (String) -> Unit,
    onSeriesSelected: (String?) -> Unit,
    onAddSeries: () -> Unit,
    onVolumeNumberChange: (String) -> Unit,
    onIsbnChange: (String) -> Unit,
    onPublisherFrChange: (String) -> Unit,
    onOriginalAuthorChange: (String) -> Unit,
    onDemographicChange: (MangaDemographic?) -> Unit,
    onPageCountChange: (String) -> Unit,
    onStatusChange: (ItemStatus) -> Unit,
    onRatingChange: (Int) -> Unit,
    onCoverPicked: (String) -> Unit,
    onCoverRemoved: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val titleRes = if (state.isEditing) R.string.manga_edit_title else R.string.manga_add_title

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
            SectionTitle(stringResource(R.string.add_record_section_cover))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CoverPicker(
                    previewModel = state.coverPreviewModel,
                    cacheKey = state.recordId,
                    hasCover = state.hasCover,
                    onCoverPicked = onCoverPicked,
                    onCoverRemoved = onCoverRemoved
                )
            }

            HorizontalDivider()

            SeriesDropdown(
                seriesId = state.seriesId,
                series = availableSeries,
                isError = state.seriesError,
                onSelected = onSeriesSelected,
                onAddSeries = onAddSeries
            )

            OutlinedTextField(
                value = state.volumeNumber,
                onValueChange = onVolumeNumberChange,
                label = { Text(stringResource(R.string.manga_field_volume_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.field_title)) },
                isError = state.titleError,
                supportingText = if (state.titleError) {
                    { Text(stringResource(R.string.field_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.originalAuthor,
                onValueChange = onOriginalAuthorChange,
                label = { Text(stringResource(R.string.manga_field_original_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.publisherFr,
                onValueChange = onPublisherFrChange,
                label = { Text(stringResource(R.string.manga_field_publisher_fr)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.isbn,
                onValueChange = onIsbnChange,
                label = { Text(stringResource(R.string.manga_field_isbn)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.pageCount,
                onValueChange = onPageCountChange,
                label = { Text(stringResource(R.string.manga_field_page_count)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle(stringResource(R.string.manga_field_demographic))
            DemographicChips(selected = state.demographic, onSelect = onDemographicChange)

            HorizontalDivider()

            SectionTitle(stringResource(R.string.manga_status_title))
            StatusSegmented(selected = state.status, onSelect = onStatusChange)

            SectionTitle(stringResource(R.string.add_record_rating_hint))
            RatingPicker(rating = state.rating, onRatingChange = onRatingChange)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesDropdown(
    seriesId: String?,
    series: List<Series>,
    isError: Boolean,
    onSelected: (String?) -> Unit,
    onAddSeries: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = series.firstOrNull { it.id == seriesId }
    val display = selected?.title.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.manga_field_series)) },
            placeholder = { Text(stringResource(R.string.manga_field_series_hint)) },
            isError = isError,
            supportingText = if (isError) {
                { Text(stringResource(R.string.field_required)) }
            } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("+ " + stringResource(R.string.collection_fab_new_series)) },
                onClick = {
                    expanded = false
                    onAddSeries()
                }
            )
            series.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.title) },
                    onClick = {
                        onSelected(s.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemographicChips(
    selected: MangaDemographic?,
    onSelect: (MangaDemographic?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MangaDemographic.entries.forEach { demographic ->
            FilterChip(
                selected = selected == demographic,
                onClick = { onSelect(if (selected == demographic) null else demographic) },
                label = { Text(stringResource(demographic.labelRes())) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSegmented(
    selected: ItemStatus,
    onSelect: (ItemStatus) -> Unit
) {
    val options = remember {
        listOf(
            ItemStatus.OWNED to R.string.item_status_owned,
            ItemStatus.WISHLIST to R.string.item_status_wishlist,
            ItemStatus.IGNORED to R.string.item_status_ignored
        )
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, labelRes) ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun RatingPicker(
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        (1..5).forEach { star ->
            IconButton(onClick = { onRatingChange(if (rating == star) 0 else star) }) {
                Icon(
                    imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(R.string.detail_rating_star, star),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun MangaDemographic.labelRes(): Int = when (this) {
    MangaDemographic.SHONEN -> R.string.manga_field_demographic_shonen
    MangaDemographic.SHOJO -> R.string.manga_field_demographic_shojo
    MangaDemographic.SEINEN -> R.string.manga_field_demographic_seinen
    MangaDemographic.JOSEI -> R.string.manga_field_demographic_josei
    MangaDemographic.KODOMO -> R.string.manga_field_demographic_kodomo
    MangaDemographic.OTHER -> R.string.manga_field_demographic_other
}
