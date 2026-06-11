package fr.beudaert.acervatim.ui.edit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervatim.domain.repository.CoverStorage
import fr.beudaert.acervatim.domain.model.AcervatimFormat
import fr.beudaert.acervatim.domain.model.Record
import fr.beudaert.acervatim.domain.model.ItemStatus
import fr.beudaert.acervatim.domain.model.RecordingSpeed
import fr.beudaert.acervatim.domain.model.normalizeArtistName
import fr.beudaert.acervatim.domain.usecase.AddRecordUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveArtistSuggestionsUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveRecordByIdUseCase
import fr.beudaert.acervatim.domain.usecase.RecordInput
import fr.beudaert.acervatim.domain.usecase.UpdateRecordUseCase
import fr.beudaert.acervatim.ui.navigation.AcervatimNavArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEditRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeRecord: ObserveRecordByIdUseCase,
    observeArtistSuggestions: ObserveArtistSuggestionsUseCase,
    private val addRecord: AddRecordUseCase,
    private val updateRecord: UpdateRecordUseCase,
    private val coverStorage: CoverStorage
) : ViewModel() {

    private val navRecordId: String? = savedStateHandle.get<String>(AcervatimNavArgs.RECORD_ID)
    private val navBarcode: String? = savedStateHandle.get<String>(AcervatimNavArgs.BARCODE)
    private val isEditing: Boolean = navRecordId != null
    private var originalRecord: Record? = null

    private val _formState = MutableStateFlow(
        AddRecordFormState(
            recordId = navRecordId ?: UUID.randomUUID().toString(),
            isEditing = isEditing,
            isLoading = isEditing,
            barcode = if (!isEditing) navBarcode.orEmpty() else ""
        )
    )
    val formState: StateFlow<AddRecordFormState> = _formState.asStateFlow()

    val artistSuggestions: StateFlow<List<String>> = observeArtistSuggestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableStateFlow<AddRecordEvent?>(null)
    val events: StateFlow<AddRecordEvent?> = _events.asStateFlow()

    init {
        if (isEditing) loadExistingRecord(navRecordId!!)
    }

    private fun loadExistingRecord(id: String) {
        viewModelScope.launch {
            val record = observeRecord(id).first()
            if (record == null) {
                _events.value = AddRecordEvent.NotFound
                return@launch
            }
            originalRecord = record
            _formState.update { it.fromRecord(record) }
        }
    }

    fun onArtistInputChange(value: String) = _formState.update {
        it.copy(artistInput = value)
    }

    fun onArtistCommit(name: String = _formState.value.artistInput) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val normalized = normalizeArtistName(trimmed)
        _formState.update { current ->
            val alreadyPresent = current.artists.any { normalizeArtistName(it) == normalized }
            if (alreadyPresent) current.copy(artistInput = "")
            else current.copy(artists = current.artists + trimmed, artistInput = "")
        }
    }

    fun onArtistRemove(index: Int) = _formState.update { current ->
        if (index !in current.artists.indices) current
        else current.copy(artists = current.artists.toMutableList().apply { removeAt(index) })
    }

    fun onTitleChange(value: String) = _formState.update { it.copy(title = value) }
    fun onYearChange(value: String) = _formState.update { it.copy(year = value.filter(Char::isDigit).take(4)) }
    fun onGenreChange(value: String) = _formState.update { it.copy(genre = value) }
    fun onLabelChange(value: String) = _formState.update { it.copy(label = value) }
    fun onCountryChange(value: String) = _formState.update { it.copy(country = value) }
    fun onFormatChange(value: AcervatimFormat?) = _formState.update { it.copy(format = value) }
    fun onRecordingSpeedChange(value: RecordingSpeed?) = _formState.update { it.copy(recordingSpeed = value) }
    fun onBarcodeChange(value: String) = _formState.update { it.copy(barcode = value) }
    fun onRatingChange(value: Int) = _formState.update {
        it.copy(rating = value.coerceIn(0, 5))
    }
    fun onPriceChange(value: String) {
        val sanitized = value.replace(',', '.').filter { it.isDigit() || it == '.' }
        val dotIndex = sanitized.indexOf('.')
        val cleaned = if (dotIndex >= 0) {
            sanitized.substring(0, dotIndex + 1) + sanitized.substring(dotIndex + 1).filter { it.isDigit() }
        } else sanitized
        _formState.update { it.copy(purchasePrice = cleaned) }
    }
    fun onWishlistToggle(value: Boolean) = _formState.update {
        it.copy(status = if (value) ItemStatus.WISHLIST else ItemStatus.OWNED)
    }

    fun onCoverPicked(uriString: String) = _formState.update {
        it.copy(pendingCoverUri = uriString)
    }

    fun onCoverRemoved() = _formState.update {
        it.copy(pendingCoverUri = null, persistedCoverPath = null, coverRemoteUrl = null)
    }

    fun onEventConsumed() {
        _events.value = null
    }

    fun save() {
        // If the input has a pending artist value, commit it before saving.
        val pending = _formState.value.artistInput.trim()
        if (pending.isNotEmpty()) onArtistCommit(pending)

        val current = _formState.value
        if (!current.isValid) {
            _formState.update { it.copy(showValidationErrors = true) }
            return
        }
        viewModelScope.launch {
            val coverPath = resolveCoverPath(current)
            val input = current.toInput(coverPath)
            val existing = originalRecord
            if (existing == null) {
                addRecord(input, id = current.recordId)
            } else {
                updateRecord(existing, input)
            }
            _events.value = AddRecordEvent.Saved
        }
    }

    private suspend fun resolveCoverPath(state: AddRecordFormState): String? {
        val pending = state.pendingCoverUri
        if (pending != null) {
            return coverStorage.saveFromUri(state.recordId, pending)
        }
        return state.persistedCoverPath
    }
}

@Immutable
data class AddRecordFormState(
    val recordId: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val collectionType: fr.beudaert.acervatim.domain.model.CollectionType =
        fr.beudaert.acervatim.domain.model.CollectionType.VINYL,
    val seriesId: String? = null,
    val volumeNumber: Int? = null,
    val artists: List<String> = emptyList(),
    val artistInput: String = "",
    val title: String = "",
    val year: String = "",
    val genre: String = "",
    val label: String = "",
    val country: String = "",
    val format: AcervatimFormat? = null,
    val recordingSpeed: RecordingSpeed? = null,
    val rating: Int = 0,
    val purchasePrice: String = "",
    val barcode: String = "",
    val status: ItemStatus = ItemStatus.OWNED,
    val persistedCoverPath: String? = null,
    val pendingCoverUri: String? = null,
    val coverRemoteUrl: String? = null,
    val discogsId: Long? = null,
    val showValidationErrors: Boolean = false
) {
    val isValid: Boolean
        get() = (artists.isNotEmpty() || artistInput.isNotBlank()) &&
                title.isNotBlank() &&
                !priceMalformed
    val artistsError: Boolean
        get() = showValidationErrors && artists.isEmpty() && artistInput.isBlank()
    val titleError: Boolean get() = showValidationErrors && title.isBlank()
    val priceError: Boolean get() = showValidationErrors && priceMalformed
    val hasCover: Boolean get() = pendingCoverUri != null || persistedCoverPath != null
    val coverPreviewModel: Any? get() = pendingCoverUri ?: persistedCoverPath

    private val priceMalformed: Boolean
        get() = purchasePrice.isNotBlank() && purchasePrice.toDoubleOrNull() == null

    internal fun parsedPrice(): Double? = purchasePrice.takeIf { it.isNotBlank() }?.toDoubleOrNull()
}

internal fun AddRecordFormState.toInput(coverPath: String?): RecordInput {
    val finalArtists = if (artistInput.isBlank()) artists else artists + artistInput.trim()
    return RecordInput(
        artists = finalArtists,
        title = title,
        collectionType = collectionType,
        seriesId = seriesId,
        volumeNumber = volumeNumber,
        genre = genre,
        year = year.toIntOrNull(),
        label = label,
        format = format,
        recordingSpeed = recordingSpeed,
        country = country,
        purchasePrice = parsedPrice(),
        rating = rating.takeIf { it > 0 },
        coverPath = coverPath,
        coverRemoteUrl = coverRemoteUrl,
        barcode = barcode,
        discogsId = discogsId,
        status = status
    )
}

internal fun AddRecordFormState.fromRecord(record: Record): AddRecordFormState = copy(
    recordId = record.id,
    isEditing = true,
    isLoading = false,
    collectionType = record.collectionType,
    seriesId = record.seriesId,
    volumeNumber = record.volumeNumber,
    artists = record.artists.map { it.name },
    artistInput = "",
    title = record.title,
    year = record.year?.toString().orEmpty(),
    genre = record.genre.orEmpty(),
    label = record.label.orEmpty(),
    country = record.country.orEmpty(),
    format = record.format,
    recordingSpeed = record.recordingSpeed,
    rating = record.rating ?: 0,
    purchasePrice = record.purchasePrice?.let { "%.2f".format(it).replace(',', '.') }.orEmpty(),
    barcode = record.barcode.orEmpty(),
    status = record.status,
    persistedCoverPath = record.coverPath,
    pendingCoverUri = null,
    coverRemoteUrl = record.coverRemoteUrl,
    discogsId = record.discogsId,
    showValidationErrors = false
)

sealed interface AddRecordEvent {
    data object Saved : AddRecordEvent
    data object NotFound : AddRecordEvent
}
