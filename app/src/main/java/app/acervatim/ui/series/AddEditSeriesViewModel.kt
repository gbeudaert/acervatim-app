package app.acervatim.ui.series

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.usecase.DuplicateSeriesException
import app.acervatim.domain.usecase.ObserveSeriesByIdUseCase
import app.acervatim.domain.usecase.SeriesInput
import app.acervatim.domain.usecase.UpsertSeriesUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEditSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeSeries: ObserveSeriesByIdUseCase,
    private val upsertSeries: UpsertSeriesUseCase,
    private val coverStorage: CoverStorage
) : ViewModel() {

    private val navSeriesId: String? = savedStateHandle.get<String>(AcervatimNavArgs.SERIES_ID)
    private val navCollectionType: CollectionType = savedStateHandle
        .get<String>(AcervatimNavArgs.COLLECTION_TYPE)
        ?.let { runCatching { CollectionType.valueOf(it) }.getOrNull() }
        ?: CollectionType.MANGA
    private val isEditing: Boolean = navSeriesId != null
    private var originalSeries: Series? = null

    private val _formState = MutableStateFlow(
        AddSeriesFormState(
            seriesId = navSeriesId ?: UUID.randomUUID().toString(),
            collectionType = navCollectionType,
            isEditing = isEditing,
            isLoading = isEditing
        )
    )
    val formState: StateFlow<AddSeriesFormState> = _formState.asStateFlow()

    private val _events = MutableStateFlow<AddSeriesEvent?>(null)
    val events: StateFlow<AddSeriesEvent?> = _events.asStateFlow()

    init {
        if (isEditing) loadExisting(navSeriesId!!)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val series = observeSeries(id).first()
            if (series == null) {
                _events.value = AddSeriesEvent.NotFound
                return@launch
            }
            originalSeries = series
            _formState.update { it.fromSeries(series) }
        }
    }

    fun onTitleChange(value: String) = _formState.update { it.copy(title = value) }
    fun onAuthorChange(value: String) = _formState.update { it.copy(originalAuthor = value) }
    fun onPublicationStatusChange(value: String) =
        _formState.update { it.copy(publicationStatus = value) }
    fun onTotalVolumesChange(value: String) = _formState.update {
        it.copy(totalVolumes = value.filter(Char::isDigit).take(4))
    }
    fun onSynopsisChange(value: String) = _formState.update { it.copy(synopsis = value) }

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
            val input = SeriesInput(
                title = current.title,
                collectionType = current.collectionType,
                originalAuthor = current.originalAuthor,
                publicationStatus = current.publicationStatus,
                totalVolumes = current.totalVolumes.toIntOrNull(),
                synopsis = current.synopsis,
                coverPath = coverPath
            )
            try {
                val saved = upsertSeries(input, existing = originalSeries)
                _events.value = AddSeriesEvent.Saved(saved.id)
            } catch (e: DuplicateSeriesException) {
                _events.value = AddSeriesEvent.DuplicateExists(e.existingSeriesId)
            }
        }
    }

    private suspend fun resolveCoverPath(state: AddSeriesFormState): String? {
        val pending = state.pendingCoverUri
        if (pending != null) {
            // Le coverPath des séries partage le même répertoire que celui des records :
            // l'id est unique (UUID), aucune collision possible.
            return coverStorage.saveFromUri(state.seriesId, pending)
        }
        return state.persistedCoverPath
    }
}

@Immutable
data class AddSeriesFormState(
    val seriesId: String = "",
    val collectionType: CollectionType = CollectionType.MANGA,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val title: String = "",
    val originalAuthor: String = "",
    val publicationStatus: String = "",
    val totalVolumes: String = "",
    val synopsis: String = "",
    val persistedCoverPath: String? = null,
    val pendingCoverUri: String? = null,
    val showValidationErrors: Boolean = false
) {
    val isValid: Boolean get() = title.isNotBlank()
    val titleError: Boolean get() = showValidationErrors && title.isBlank()
    val hasCover: Boolean get() = pendingCoverUri != null || persistedCoverPath != null
    val coverPreviewModel: Any? get() = pendingCoverUri ?: persistedCoverPath
}

internal fun AddSeriesFormState.fromSeries(series: Series): AddSeriesFormState = copy(
    seriesId = series.id,
    collectionType = series.collectionType,
    isEditing = true,
    isLoading = false,
    title = series.title,
    originalAuthor = series.originalAuthor.orEmpty(),
    publicationStatus = series.publicationStatus.orEmpty(),
    totalVolumes = series.totalVolumes?.toString().orEmpty(),
    synopsis = series.synopsis.orEmpty(),
    persistedCoverPath = series.coverPath,
    pendingCoverUri = null,
    showValidationErrors = false
)

sealed interface AddSeriesEvent {
    data class Saved(val seriesId: String) : AddSeriesEvent
    data object NotFound : AddSeriesEvent
    /** Une autre série partage la même clé naturelle. `existingSeriesId` permet d'ouvrir l'existante. */
    data class DuplicateExists(val existingSeriesId: String) : AddSeriesEvent
}
