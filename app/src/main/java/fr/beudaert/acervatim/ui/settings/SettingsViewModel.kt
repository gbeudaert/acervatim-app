package fr.beudaert.acervatim.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.beudaert.acervatim.domain.model.CollectionType
import fr.beudaert.acervatim.domain.model.ListMode
import fr.beudaert.acervatim.domain.usecase.ExportBackupUseCase
import fr.beudaert.acervatim.domain.usecase.ImportBackupUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveDuplicateAlbumsSettingUseCase
import fr.beudaert.acervatim.domain.usecase.ObserveListModeForCollectionTypeUseCase
import fr.beudaert.acervatim.domain.usecase.SetDuplicateAlbumsSettingUseCase
import fr.beudaert.acervatim.domain.usecase.SetListModeForCollectionTypeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeDuplicate: ObserveDuplicateAlbumsSettingUseCase,
    observeListMode: ObserveListModeForCollectionTypeUseCase,
    private val setDuplicate: SetDuplicateAlbumsSettingUseCase,
    private val setListMode: SetListModeForCollectionTypeUseCase,
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val duplicateAlbumsPerArtist: StateFlow<Boolean> = observeDuplicate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Mode (FLAT / SERIAL) effectif pour chaque [CollectionType], lu depuis DataStore.
     * Comporte une entrée pour tous les types, même ceux qui ne supportent pas SERIAL :
     * c'est à l'UI de griser / cacher les choix selon [CollectionType.supportsSerial].
     */
    val listModeByType: StateFlow<Map<CollectionType, ListMode>> = combine(
        CollectionType.entries.map { type -> observeListMode(type) }
    ) { modes ->
        CollectionType.entries.zip(modes.toList()).toMap()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionType.entries.associateWith { it.defaultListMode }
    )

    fun onDuplicateAlbumsToggle(value: Boolean) {
        viewModelScope.launch {
            setDuplicate(value)
        }
    }

    fun onListModeChange(type: CollectionType, mode: ListMode) {
        viewModelScope.launch {
            setListMode(type, mode)
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
