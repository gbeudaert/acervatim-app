package app.acervatim.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.usecase.CollectionTypeStats
import app.acervatim.domain.usecase.ObserveCollectionTypeStatsUseCase
import app.acervatim.domain.usecase.ObserveVisibleCollectionTypesUseCase
import app.acervatim.domain.usecase.SetCollectionTypeVisibleUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageCollectionTypesViewModel @Inject constructor(
    observeVisible: ObserveVisibleCollectionTypesUseCase,
    observeStats: ObserveCollectionTypeStatsUseCase,
    private val setVisible: SetCollectionTypeVisibleUseCase
) : ViewModel() {

    val uiState: StateFlow<List<CollectionTypeVisibilityUiState>> = combine(
        observeVisible(),
        observeStats()
    ) { visible, stats ->
        // On ne peut pas décocher le dernier type visible : la liste resterait sans collection.
        val canHide = visible.size > 1
        CollectionType.entries.map { type ->
            CollectionTypeVisibilityUiState(
                type = type,
                visible = type in visible,
                itemCount = stats.firstOrNull { it.collectionType == type }?.displayCount ?: 0,
                toggleEnabled = type !in visible || canHide
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionType.entries.map {
            CollectionTypeVisibilityUiState(type = it, visible = true, itemCount = 0, toggleEnabled = true)
        }
    )

    fun onVisibilityChange(type: CollectionType, visible: Boolean) {
        viewModelScope.launch {
            setVisible(type, visible)
        }
    }
}

@Immutable
data class CollectionTypeVisibilityUiState(
    val type: CollectionType,
    val visible: Boolean,
    val itemCount: Int,
    /** `false` lorsqu'il s'agit du dernier type visible : on bloque sa désactivation. */
    val toggleEnabled: Boolean
)
