package fr.beudaert.acervus.ui.scan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervus.domain.repository.RecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    private val recordRepository: RecordRepository
) : ViewModel() {

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
            val existing = recordRepository.findByBarcode(sanitized)
            if (existing != null) {
                _state.update {
                    it.copy(detection = DetectionStep.Found(sanitized, existing.title))
                }
                _events.value = BarcodeScanEvent.OpenExistingRecord(existing.id, existing.title)
            } else {
                _state.update {
                    it.copy(detection = DetectionStep.NotInCollection(sanitized))
                }
                _events.value = BarcodeScanEvent.ProposeNewRecord(sanitized)
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
    data class NotInCollection(val barcode: String) : DetectionStep
}

sealed interface BarcodeScanEvent {
    data class OpenExistingRecord(val recordId: String, val title: String) : BarcodeScanEvent
    data class ProposeNewRecord(val barcode: String) : BarcodeScanEvent
}
