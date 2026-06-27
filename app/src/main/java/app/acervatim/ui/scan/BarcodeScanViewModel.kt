package app.acervatim.ui.scan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.usecase.LookupByBarcodeUseCase
import app.acervatim.domain.usecase.LookupResult
import app.acervatim.ui.navigation.AcervatimNavArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordRepository: RecordRepository,
    private val lookupByBarcode: LookupByBarcodeUseCase,
    private val prefillHandoff: ScanPrefillHandoff
) : ViewModel() {

    /** Type de collection actif au moment du scan (porté par `SCAN_ROUTE`). Défaut : vinyle. */
    private val collectionType: CollectionType =
        savedStateHandle.get<String>(AcervatimNavArgs.COLLECTION_TYPE)
            ?.let { runCatching { CollectionType.valueOf(it) }.getOrNull() }
            ?: CollectionType.VINYL

    private val _state = MutableStateFlow(BarcodeScanUiState())
    val state: StateFlow<BarcodeScanUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<BarcodeScanEvent?>(null)
    val events: StateFlow<BarcodeScanEvent?> = _events.asStateFlow()

    fun onCameraPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted, permissionResolved = true) }
    }

    fun onBarcodeDetected(rawValue: String) {
        val sanitized = rawValue.trim()
        if (sanitized.isEmpty()) return
        if (_state.value.detection !is DetectionStep.Idle) return
        _state.update { it.copy(detection = DetectionStep.Looking(sanitized)) }
        viewModelScope.launch {
            // 1) Dédup locale prioritaire : gratuite, hors-ligne, évite un doublon.
            val existing = recordRepository.findByBarcode(sanitized)
            if (existing != null) {
                _state.update { it.copy(detection = DetectionStep.Found(sanitized, existing.title)) }
                _events.value = BarcodeScanEvent.OpenExistingRecord(existing.id, existing.title)
                return@launch
            }

            // 2) Pas en collection → tentative d'enrichissement distant (si connecté).
            _state.update { it.copy(detection = DetectionStep.Enriching(sanitized)) }
            when (val result = lookupByBarcode(sanitized, collectionType.typeCode)) {
                is LookupResult.Matches -> {
                    // V1 : on retient le meilleur résultat. Un sélecteur multi-résultats viendra plus tard.
                    prefillHandoff.put(sanitized, result.matches.first())
                    _state.update {
                        it.copy(detection = DetectionStep.Enriched(sanitized, result.matches.first().title))
                    }
                    _events.value = BarcodeScanEvent.ProposeNewRecord(sanitized)
                }
                // Aucun résultat / non connecté / erreur réseau : repli sur la saisie manuelle.
                LookupResult.NoMatch,
                LookupResult.NotConnected,
                is LookupResult.Error -> {
                    _state.update { it.copy(detection = DetectionStep.NotInCollection(sanitized)) }
                    _events.value = BarcodeScanEvent.ProposeNewRecord(sanitized)
                }
            }
        }
    }

    fun onEventConsumed() {
        _events.value = null
    }

    fun reset() {
        _state.update { it.copy(detection = DetectionStep.Idle) }
        _events.value = null
    }
}

@Immutable
data class BarcodeScanUiState(
    val permissionResolved: Boolean = false,
    val permissionGranted: Boolean = false,
    val detection: DetectionStep = DetectionStep.Idle
)

sealed interface DetectionStep {
    data object Idle : DetectionStep
    data class Looking(val barcode: String) : DetectionStep
    data class Found(val barcode: String, val title: String) : DetectionStep
    /** Recherche de métadonnées en cours auprès du backend. */
    data class Enriching(val barcode: String) : DetectionStep
    /** Métadonnées trouvées : le formulaire d'ajout sera pré-rempli. */
    data class Enriched(val barcode: String, val title: String) : DetectionStep
    data class NotInCollection(val barcode: String) : DetectionStep
}

sealed interface BarcodeScanEvent {
    data class OpenExistingRecord(val recordId: String, val title: String) : BarcodeScanEvent
    data class ProposeNewRecord(val barcode: String) : BarcodeScanEvent
}
