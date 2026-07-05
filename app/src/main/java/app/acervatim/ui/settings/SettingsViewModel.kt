package app.acervatim.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import app.acervatim.domain.usecase.ExportBackupUseCase
import app.acervatim.domain.usecase.ImportBackupUseCase
import app.acervatim.domain.usecase.ObserveDuplicateAlbumsSettingUseCase
import app.acervatim.domain.usecase.SetDuplicateAlbumsSettingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeDuplicate: ObserveDuplicateAlbumsSettingUseCase,
    private val setDuplicate: SetDuplicateAlbumsSettingUseCase,
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val duplicateAlbumsPerArtist: StateFlow<Boolean> = observeDuplicate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onDuplicateAlbumsToggle(value: Boolean) {
        viewModelScope.launch {
            setDuplicate(value)
        }
    }

    private val _backupState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val backupState: StateFlow<BackupOpState> = _backupState.asStateFlow()

    fun onExportTo(uri: Uri) {
        if (_backupState.value is BackupOpState.Busy) return
        _backupState.value = BackupOpState.Busy
        viewModelScope.launch {
            val outcome = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    exportBackup(output)
                } ?: throw IOException("Impossible d'ouvrir le fichier en écriture")
            }
            _backupState.value = outcome.fold(
                onSuccess = { result ->
                    BackupOpState.ExportDone(
                        recordCount = result.recordCount,
                        seriesCount = result.seriesCount
                    )
                },
                onFailure = { BackupOpState.Error(it.message ?: it.javaClass.simpleName) }
            )
        }
    }

    fun onImportFrom(uri: Uri) {
        if (_backupState.value is BackupOpState.Busy) return
        _backupState.value = BackupOpState.Busy
        viewModelScope.launch {
            val outcome = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    importBackup(input)
                } ?: throw IOException("Impossible d'ouvrir le fichier en lecture")
            }
            _backupState.value = outcome.fold(
                onSuccess = { result ->
                    BackupOpState.ImportDone(
                        recordCount = result.recordCount,
                        seriesCount = result.seriesCount
                    )
                },
                onFailure = { BackupOpState.Error(it.message ?: it.javaClass.simpleName) }
            )
        }
    }

    /** Acquitte le dernier message (succès / erreur) pour repasser à Idle. */
    fun consumeBackupMessage() {
        if (_backupState.value !is BackupOpState.Busy) {
            _backupState.value = BackupOpState.Idle
        }
    }
}

sealed interface BackupOpState {
    data object Idle : BackupOpState
    data object Busy : BackupOpState
    data class ExportDone(val recordCount: Int, val seriesCount: Int) : BackupOpState
    data class ImportDone(val recordCount: Int, val seriesCount: Int) : BackupOpState
    data class Error(val message: String) : BackupOpState
}
