package app.acervatim.ui.series

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.usecase.ImportResult
import app.acervatim.domain.usecase.ImportScannedSeriesUseCase
import app.acervatim.ui.navigation.AcervatimNavArgs
import app.acervatim.ui.scan.ScanPrefillHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pilote l'écran d'import de série lancé après un scan ISBN depuis la **vue séries**. Récupère le
 * résultat enrichi déposé par le scan ([ScanPrefillHandoff]) pour proposer l'import « tous les tomes
 * connus » (édition complète via le backend) ou « tome scanné seul ».
 *
 * Si aucun résultat n'a été déposé (hors-ligne, code inconnu), l'écran propose un repli vers la
 * saisie manuelle. Voir `docs/interne/spec-scan-import-serie.md`.
 */
@HiltViewModel
class SeriesImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    prefillHandoff: ScanPrefillHandoff,
    private val importSeries: ImportScannedSeriesUseCase
) : ViewModel() {

    private val barcode: String = savedStateHandle.get<String>(AcervatimNavArgs.BARCODE).orEmpty()
    private val match: ExternalMatch? =
        barcode.takeIf { it.isNotBlank() }?.let { prefillHandoff.take(it) }

    private val _state = MutableStateFlow(
        SeriesImportUiState(
            barcode = barcode,
            hasMatch = match != null,
            seriesTitle = match?.let { it.seriesTitleFr ?: it.title },
            scannedVolume = match?.volumeNumber
        )
    )
    val state: StateFlow<SeriesImportUiState> = _state.asStateFlow()

    fun onToggleImportAll(value: Boolean) = _state.update { it.copy(importAll = value) }

    fun onImport() {
        val current = match ?: return
        if (_state.value.importing || _state.value.result != null) return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            val result = importSeries.importFromList(
                scannedIsbn = barcode,
                match = current,
                importAllKnownTomes = _state.value.importAll
            )
            _state.update { it.copy(importing = false, result = result) }
        }
    }
}

@Immutable
data class SeriesImportUiState(
    val barcode: String = "",
    val hasMatch: Boolean = false,
    val seriesTitle: String? = null,
    val scannedVolume: Int? = null,
    val importAll: Boolean = true,
    val importing: Boolean = false,
    val result: ImportResult? = null
)
