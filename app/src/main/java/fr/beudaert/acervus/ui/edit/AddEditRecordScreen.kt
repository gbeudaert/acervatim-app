package fr.beudaert.acervus.ui.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.beudaert.acervus.R
import fr.beudaert.acervus.domain.model.AcervusFormat
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.RecordingSpeed
import fr.beudaert.acervus.domain.model.normalizeArtistName
import fr.beudaert.acervus.ui.theme.AcervusTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun AddEditRecordScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AddEditRecordViewModel = hiltViewModel()
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val suggestions by viewModel.artistSuggestions.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()

    LaunchedEffect(event) {
        when (event) {
            AddRecordEvent.Saved, AddRecordEvent.NotFound -> {
                viewModel.onEventConsumed()
                onSaved()
            }
            null -> Unit
        }
    }

    AddEditRecordContent(
        state = state,
        artistSuggestions = suggestions,
        onArtistInputChange = viewModel::onArtistInputChange,
        onArtistCommit = viewModel::onArtistCommit,
        onArtistRemove = viewModel::onArtistRemove,
        onTitleChange = viewModel::onTitleChange,
        onYearChange = viewModel::onYearChange,
        onGenreChange = viewModel::onGenreChange,
        onLabelChange = viewModel::onLabelChange,
        onCountryChange = viewModel::onCountryChange,
        onFormatChange = viewModel::onFormatChange,
        onRecordingSpeedChange = viewModel::onRecordingSpeedChange,
        onPriceChange = viewModel::onPriceChange,
        onBarcodeChange = viewModel::onBarcodeChange,
        onRatingChange = viewModel::onRatingChange,
        onWishlistToggle = viewModel::onWishlistToggle,
        onCoverPicked = viewModel::onCoverPicked,
        onCoverRemoved = viewModel::onCoverRemoved,
        onSave = viewModel::save,
        onCancel = onCancel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEditRecordContent(
    state: AddRecordFormState,
    artistSuggestions: List<String>,
    onArtistInputChange: (String) -> Unit,
    onArtistCommit: (String) -> Unit,
    onArtistRemove: (Int) -> Unit,
    onTitleChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onGenreChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onFormatChange: (AcervusFormat?) -> Unit,
    onRecordingSpeedChange: (RecordingSpeed?) -> Unit,
    onPriceChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onRatingChange: (Int) -> Unit,
    onWishlistToggle: (Boolean) -> Unit,
    onCoverPicked: (String) -> Unit,
    onCoverRemoved: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleRes = if (state.isEditing) R.string.edit_record_title else R.string.add_record_title

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            BottomActions(onCancel = onCancel, onSave = onSave, saveEnabled = state.isValid)
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
            CoverPickerCard(
                state = state,
                onCoverPicked = onCoverPicked,
                onCoverRemoved = onCoverRemoved
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.add_record_section_identity))
            ArtistsField(
                artists = state.artists,
                input = state.artistInput,
                suggestions = artistSuggestions,
                isError = state.artistsError,
                onInputChange = onArtistInputChange,
                onCommit = onArtistCommit,
                onRemove = onArtistRemove
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
                value = state.year,
                onValueChange = onYearChange,
                label = { Text(stringResource(R.string.field_year)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.add_record_section_edition))
            OutlinedTextField(
                value = state.label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(R.string.field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FormatPicker(selected = state.format, onSelect = onFormatChange)
            RecordingSpeedPicker(selected = state.recordingSpeed, onSelect = onRecordingSpeedChange)
            OutlinedTextField(
                value = state.country,
                onValueChange = onCountryChange,
                label = { Text(stringResource(R.string.field_country)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.add_record_section_classification))
            OutlinedTextField(
                value = state.genre,
                onValueChange = onGenreChange,
                label = { Text(stringResource(R.string.field_genre)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            RatingPicker(rating = state.rating, onRatingChange = onRatingChange)

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.add_record_section_purchase))
            OutlinedTextField(
                value = state.purchasePrice,
                onValueChange = onPriceChange,
                label = { Text(stringResource(R.string.field_price)) },
                singleLine = true,
                isError = state.priceError,
                supportingText = if (state.priceError) {
                    { Text(stringResource(R.string.field_invalid)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.barcode,
                onValueChange = onBarcodeChange,
                label = { Text(stringResource(R.string.field_barcode)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.add_record_section_status))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_record_wishlist_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.status == ItemStatus.WISHLIST,
                    onCheckedChange = onWishlistToggle
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ArtistsField(
    artists: List<String>,
    input: String,
    suggestions: List<String>,
    isError: Boolean,
    onInputChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    onRemove: (Int) -> Unit
) {
    val filteredSuggestions = remember(input, suggestions, artists) {
        if (input.isBlank()) emptyList()
        else {
            val normalizedInput = normalizeArtistName(input)
            val alreadyPresent = artists.map { normalizeArtistName(it) }.toSet()
            suggestions
                .filter { name ->
                    val n = normalizeArtistName(name)
                    n != normalizedInput &&
                            n.contains(normalizedInput) &&
                            n !in alreadyPresent
                }
                .take(MAX_SUGGESTIONS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (artists.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                artists.forEachIndexed { index, name ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.add_record_artist_remove,
                                    name
                                ),
                                modifier = Modifier.size(InputChipDefaults.IconSize)
                            )
                        },
                        modifier = Modifier.clickable { onRemove(index) }
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.field_artists)) },
                placeholder = { Text(stringResource(R.string.field_artists_placeholder)) },
                isError = isError,
                supportingText = when {
                    isError -> { { Text(stringResource(R.string.field_required)) } }
                    else -> { { Text(stringResource(R.string.field_artists_hint)) } }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit(input) }),
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = filteredSuggestions.isNotEmpty(),
                onDismissRequest = { },
                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
            ) {
                filteredSuggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = { onCommit(suggestion) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverPickerCard(
    state: AddRecordFormState,
    onCoverPicked: (String) -> Unit,
    onCoverRemoved: () -> Unit
) {
    val context = LocalContext.current
    var sheetVisible by remember { mutableStateOf(false) }
    var showCameraRationale by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { onCoverPicked(it.toString()) }
        }
        pendingCameraUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onCoverPicked(it.toString()) }
    }

    fun launchCamera() {
        val uri = createCameraUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else showCameraRationale = true
    }

    fun requestCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    CoverThumbnail(
        previewModel = state.coverPreviewModel,
        recordId = state.recordId,
        onClick = { sheetVisible = true }
    )

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_record_cover_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                ListItem(
                    leadingContent = {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    },
                    headlineContent = {
                        Text(stringResource(R.string.add_record_cover_take_photo))
                    },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetVisible = false
                            requestCamera()
                        }
                    }
                )
                ListItem(
                    leadingContent = {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    },
                    headlineContent = {
                        Text(stringResource(R.string.add_record_cover_pick_gallery))
                    },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetVisible = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    }
                )
                if (state.hasCover) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.add_record_cover_remove),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                sheetVisible = false
                                onCoverRemoved()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text(stringResource(R.string.permission_camera_required_title)) },
            text = { Text(stringResource(R.string.permission_camera_required_message)) },
            confirmButton = {
                TextButton(onClick = { showCameraRationale = false }) {
                    Text(stringResource(R.string.action_back))
                }
            }
        )
    }
}

@Composable
private fun CoverThumbnail(
    previewModel: Any?,
    recordId: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val data: Any? = when (previewModel) {
            is String -> if (previewModel.startsWith("content:") || previewModel.startsWith("file:")) {
                previewModel
            } else {
                File(previewModel)
            }
            else -> previewModel
        }
        if (data != null) {
            val context = LocalContext.current
            val request = ImageRequest.Builder(context)
                .data(data)
                .crossfade(true)
                .memoryCacheKey(recordId)
                .build()
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = stringResource(R.string.add_record_cover_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RatingPicker(
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.add_record_rating_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                val starIndex = index + 1
                val filled = starIndex <= rating
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(R.string.detail_rating_star, starIndex),
                    tint = if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            onRatingChange(if (rating == starIndex) 0 else starIndex)
                        }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatPicker(
    selected: AcervusFormat?,
    onSelect: (AcervusFormat?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.field_format),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcervusFormat.entries.forEach { format ->
                FilterChip(
                    selected = selected == format,
                    onClick = {
                        onSelect(if (selected == format) null else format)
                    },
                    label = { Text(format.label()) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingSpeedPicker(
    selected: RecordingSpeed?,
    onSelect: (RecordingSpeed?) -> Unit
) {
    val entries = RecordingSpeed.entries
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.field_recording_speed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, speed ->
                SegmentedButton(
                    selected = selected == speed,
                    onClick = { onSelect(if (selected == speed) null else speed) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                    label = { Text(speed.label()) }
                )
            }
        }
    }
}

@Composable
private fun BottomActions(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean
) {
    Surface(tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()
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
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = saveEnabled) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun AcervusFormat.label(): String = when (this) {
    AcervusFormat.LP_33 -> stringResource(R.string.format_lp_33)
    AcervusFormat.SINGLE_45 -> stringResource(R.string.format_single_45)
    AcervusFormat.SHELLAC_78 -> stringResource(R.string.format_shellac_78)
    AcervusFormat.OTHER -> stringResource(R.string.format_other)
}

@Composable
private fun RecordingSpeed.label(): String = when (this) {
    RecordingSpeed.RPM_33 -> stringResource(R.string.recording_speed_33)
    RecordingSpeed.RPM_45 -> stringResource(R.string.recording_speed_45)
    RecordingSpeed.RPM_78 -> stringResource(R.string.recording_speed_78)
    RecordingSpeed.OTHER -> stringResource(R.string.recording_speed_other)
}

private fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
    val file = File(dir, "cover_${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private const val MAX_SUGGESTIONS = 5

@Preview(showBackground = true)
@Composable
private fun AddEditRecordScreenPreview() {
    AcervusTheme {
        AddEditRecordContent(
            state = AddRecordFormState(),
            artistSuggestions = emptyList(),
            onArtistInputChange = {},
            onArtistCommit = {},
            onArtistRemove = {},
            onTitleChange = {},
            onYearChange = {},
            onGenreChange = {},
            onLabelChange = {},
            onCountryChange = {},
            onFormatChange = {},
            onRecordingSpeedChange = {},
            onPriceChange = {},
            onBarcodeChange = {},
            onRatingChange = {},
            onWishlistToggle = {},
            onCoverPicked = {},
            onCoverRemoved = {},
            onSave = {},
            onCancel = {}
        )
    }
}
