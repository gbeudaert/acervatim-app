package fr.beudaert.acervus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.beudaert.acervus.domain.usecase.ObserveDuplicateAlbumsSettingUseCase
import fr.beudaert.acervus.domain.usecase.SetDuplicateAlbumsSettingUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeDuplicate: ObserveDuplicateAlbumsSettingUseCase,
    private val setDuplicate: SetDuplicateAlbumsSettingUseCase
) : ViewModel() {

    val duplicateAlbumsPerArtist: StateFlow<Boolean> = observeDuplicate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onDuplicateAlbumsToggle(value: Boolean) {
        viewModelScope.launch {
            setDuplicate(value)
        }
    }
}
