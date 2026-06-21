package app.acervatim.ui.manga

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaDemographic
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.readInt
import app.acervatim.domain.model.readString
import app.acervatim.domain.usecase.AddRecordUseCase
import app.acervatim.domain.usecase.DuplicateRecordException
import app.acervatim.domain.usecase.ObserveRecordByIdUseCase
import app.acervatim.domain.usecase.ObserveSeriesByCollectionTypeUseCase
import app.acervatim.domain.usecase.RecordInput
import app.acervatim.domain.usecase.UpdateRecordUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
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
class AddEditMangaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeRecord: ObserveRecordByIdUseCase,
    observeSeriesByCollectionType: ObserveSeriesByCollectionTypeUseCase,
    private val addRecord: AddRecordUseCase,
    private val updateRecord: UpdateRecordUseCase,
    private val coverStorage: CoverStorage
) : ViewModel() {

    private val navRecordId: String? = savedStateHandle.get<String>(AcervatimNavArgs.RECORD_ID)
    private val navSeriesId: String? = savedStateHandle.get<String>(AcervatimNavArgs.SERIES_ID)
    private val navBarcode: String? = savedStateHandle.get<String>(AcervatimNavArgs.BARCODE)
    private val navVolumeNumber: Int? =
        savedStateHandle.get<String>(AcervatimNavArgs.VOLUME_NUMBER)?.toIntOrNull()

    private val isEditing: Boolean = navRecordId != null
    private var originalRecord: Record? = null

    private val _formState = MutableStateFlow(
        AddMangaFormState(
            recordId = navRecordId ?: UUID.randomUUID().toString(),
            isEditing = isEditing,
            isLoading = isEditing,
            seriesId = navSeriesId,
            volumeNumber = navVolumeNumber?.toString().orEmpty(),
            isbn = if (!isEditing) navBarcode.orEmpty() else ""
        )
    )
    val formState: StateFlow<AddMangaFormState> = _formState.asStateFlow()

    val availableSeries: StateFlow<List<Series>> =
        observeSeriesByCollectionType(CollectionType.MANGA)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableStateFlow<AddMangaEvent?>(null)
    val events: StateFlow<AddMangaEvent?> = _events.asStateFlow()

    init {
        if (isEditing) loadExisting(navRecordId!!)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val record = observeRecord(id).first()
            if (record == null) {
                _events.value = AddMangaEvent.NotFound
                return@launch
            }
            originalRecord = record
            _formState.update { it.fromRecord(record) }
        }
    }

    fun onTitleChange(value: String) = _formState.update { it.copy(title = value) }
    fun onSeriesSelected(seriesId: String?) = _formState.update { it.copy(seriesId = seriesId) }
    fun onVolumeNumberChange(value: String) = _formState.update {
        it.copy(volumeNumber = value.filter(Char::isDigit).take(4))
    }
    fun onIsbnChange(value: String) = _formState.update { it.copy(isbn = value) }
    fun onPublisherFrChange(value: String) = _formState.update { it.copy(publisherFr = value) }
    fun onOriginalAuthorChange(value: String) =
        _formState.update { it.copy(originalAuthor = value) }
    fun onDemographicChange(value: MangaDemographic?) =
        _formState.update { it.copy(demographic = value) }
    fun onPageCountChange(value: String) = _formState.update {
        it.copy(pageCount = value.filter(Char::isDigit).take(5))
    }
    fun onStatusChange(value: ItemStatus) = _formState.update { it.copy(status = value) }
    fun onRatingChange(value: Int) = _formState.update {
        it.copy(rating = value.coerceIn(0, 5))
    }

    fun onCoverPicked(uriString: String) = _formState.update {
        it.copy(pendingCoverUri = uriString)
    }

    fun onCoverRemoved() = _formState.update {
        it.copy(pendingCoverUri = null, persistedCoverPath = null)
    }

    fun onEventConsumed() {
        _events.value = null
    }

    fun save() {
        val current = _formState.value
        if (!current.isValid) {
            _formState.update { it.copy(showValidationErrors = true) }
            return
        }
        viewModelScope.launch {
            val coverPath = resolveCoverPath(current)
            val input = current.toInput(coverPath = coverPath)
            val existing = originalRecord
            try {
                if (existing == null) {
                    addRecord(input, id = current.recordId)
                } else {
                    updateRecord(existing, input)
                }
                _events.value = AddMangaEvent.Saved(current.recordId)
            } catch (e: DuplicateRecordException) {
                _events.value = AddMangaEvent.DuplicateIsbn(e.existingRecordId)
            }
        }
    }

    private suspend fun resolveCoverPath(state: AddMangaFormState): String? {
        val pending = state.pendingCoverUri
        if (pending != null) {
            return coverStorage.saveFromUri(state.recordId, pending)
        }
        return state.persistedCoverPath
    }
}

@Immutable
data class AddMangaFormState(
    val recordId: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val seriesId: String? = null,
    val volumeNumber: String = "",
    val title: String = "",
    val originalAuthor: String = "",
    val publisherFr: String = "",
    val isbn: String = "",
    val demographic: MangaDemographic? = null,
    val pageCount: String = "",
    val rating: Int = 0,
    val status: ItemStatus = ItemStatus.OWNED,
    val persistedCoverPath: String? = null,
    val pendingCoverUri: String? = null,
    val showValidationErrors: Boolean = false
) {
    /** Une série est requise pour un tome manga ; le titre est requis aussi. */
    val isValid: Boolean get() = title.isNotBlank() && seriesId != null
    val titleError: Boolean get() = showValidationErrors && title.isBlank()
    val seriesError: Boolean get() = showValidationErrors && seriesId == null
    val hasCover: Boolean get() = pendingCoverUri != null || persistedCoverPath != null
    val coverPreviewModel: Any? get() = pendingCoverUri ?: persistedCoverPath
}

internal fun AddMangaFormState.toInput(coverPath: String?): RecordInput {
    val extras = buildMap {
        if (publisherFr.isNotBlank()) put(MangaExtras.KEY_PUBLISHER_FR, publisherFr.trim())
        if (originalAuthor.isNotBlank()) put(MangaExtras.KEY_ORIGINAL_AUTHOR, originalAuthor.trim())
        demographic?.let { put(MangaExtras.KEY_DEMOGRAPHIC, it.name) }
        if (pageCount.isNotBlank()) put(MangaExtras.KEY_PAGE_COUNT, pageCount.trim())
    }
    return RecordInput(
        // Pour un tome de manga le concept d'« artistes » est porté par la série (auteur).
        // On exige une liste non vide côté use case : on injecte donc l'auteur original (ou
        // "Inconnu" en repli) pour conserver la contrainte d'invariant à un seul endroit.
        artists = listOf(originalAuthor.trim().takeIf { it.isNotEmpty() } ?: "Inconnu"),
        title = title.trim(),
        collectionType = CollectionType.MANGA,
        seriesId = seriesId,
        volumeNumber = volumeNumber.toIntOrNull(),
        // ISBN est désormais first-class et porte l'unicité. On n'écrit plus le barcode pour
        // un tome (le `Record.barcode` reste utile pour le vinyle uniquement).
        isbn = isbn.trim().takeIf { it.isNotEmpty() },
        coverPath = coverPath,
        rating = rating.takeIf { it > 0 },
        status = status,
        extras = extras
    )
}

internal fun AddMangaFormState.fromRecord(record: Record): AddMangaFormState = copy(
    recordId = record.id,
    isEditing = true,
    isLoading = false,
    seriesId = record.seriesId,
    volumeNumber = record.volumeNumber?.toString().orEmpty(),
    title = record.title,
    originalAuthor = record.extras.readString(MangaExtras.KEY_ORIGINAL_AUTHOR)
        ?: record.artists.firstOrNull()?.name.orEmpty(),
    publisherFr = record.extras.readString(MangaExtras.KEY_PUBLISHER_FR).orEmpty(),
    isbn = record.isbn.orEmpty(),
    demographic = MangaDemographic.fromExtras(record.extras[MangaExtras.KEY_DEMOGRAPHIC]),
    pageCount = record.extras.readInt(MangaExtras.KEY_PAGE_COUNT)?.toString().orEmpty(),
    rating = record.rating ?: 0,
    status = record.status,
    persistedCoverPath = record.coverPath,
    pendingCoverUri = null,
    showValidationErrors = false
)

sealed interface AddMangaEvent {
    data class Saved(val recordId: String) : AddMangaEvent
    data object NotFound : AddMangaEvent
    /** Un autre tome porte déjà cet ISBN. `existingRecordId` permet à l'UI de proposer "Ouvrir". */
    data class DuplicateIsbn(val existingRecordId: String) : AddMangaEvent
}
