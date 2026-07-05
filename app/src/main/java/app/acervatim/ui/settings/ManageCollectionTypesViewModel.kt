package app.acervatim.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.Source
import app.acervatim.domain.model.sources
import app.acervatim.domain.usecase.ConnectSourceUseCase
import app.acervatim.domain.usecase.DisconnectSourceUseCase
import app.acervatim.domain.usecase.ObserveCollectionTypeStatsUseCase
import app.acervatim.domain.usecase.ObserveConnectedProvidersUseCase
import app.acervatim.domain.usecase.ObserveListModeForCollectionTypeUseCase
import app.acervatim.domain.usecase.ObserveSyncedCollectionTypesUseCase
import app.acervatim.domain.usecase.ObserveVisibleCollectionTypesUseCase
import app.acervatim.domain.usecase.RefreshConnectedProvidersUseCase
import app.acervatim.domain.usecase.SetCollectionTypeSyncedUseCase
import app.acervatim.domain.usecase.SetCollectionTypeVisibleUseCase
import app.acervatim.domain.usecase.SetListModeForCollectionTypeUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageCollectionTypesViewModel @Inject constructor(
    observeVisible: ObserveVisibleCollectionTypesUseCase,
    observeSynced: ObserveSyncedCollectionTypesUseCase,
    observeStats: ObserveCollectionTypeStatsUseCase,
    observeListMode: ObserveListModeForCollectionTypeUseCase,
    observeConnected: ObserveConnectedProvidersUseCase,
    private val setVisible: SetCollectionTypeVisibleUseCase,
    private val setSynced: SetCollectionTypeSyncedUseCase,
    private val setListMode: SetListModeForCollectionTypeUseCase,
    private val connectSource: ConnectSourceUseCase,
    private val disconnectSource: DisconnectSourceUseCase,
    private val refreshConnected: RefreshConnectedProvidersUseCase
) : ViewModel() {

    private val listModes: Flow<Map<CollectionType, ListMode>> = combine(
        CollectionType.entries.map { type -> observeListMode(type) }
    ) { modes -> CollectionType.entries.zip(modes.toList()).toMap() }

    val uiState: StateFlow<List<CollectionTypeManageUiState>> = combine(
        observeVisible(),
        observeSynced(),
        observeStats(),
        listModes,
        observeConnected()
    ) { visible, synced, stats, modes, connected ->
        // On ne peut pas décocher le dernier type visible : la liste resterait sans collection.
        val canHide = visible.size > 1
        CollectionType.entries.map { type ->
            CollectionTypeManageUiState(
                type = type,
                visible = type in visible,
                visibilityToggleEnabled = type !in visible || canHide,
                synced = type in synced,
                supportsSerial = type.supportsSerial,
                listMode = modes[type] ?: type.defaultListMode,
                itemCount = stats.firstOrNull { it.collectionType == type }?.displayCount ?: 0,
                sources = type.sources.map { source ->
                    SourceUiState(source = source, connected = source.providerCode in connected)
                }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionType.entries.map { type ->
            CollectionTypeManageUiState(
                type = type,
                visible = true,
                visibilityToggleEnabled = true,
                synced = true,
                supportsSerial = type.supportsSerial,
                listMode = type.defaultListMode,
                itemCount = 0,
                sources = type.sources.map { SourceUiState(it, connected = false) }
            )
        }
    )

    private val _events = MutableSharedFlow<SourcesEvent>(extraBufferCapacity = 1)
    /** Événements one-shot pour l'UI : ouvrir l'URL d'autorisation, ou signaler une erreur. */
    val events: SharedFlow<SourcesEvent> = _events

    init {
        // État des connexions au premier affichage ; l'écran rappelle [refreshConnectedProviders]
        // à son retour au premier plan (après le passage dans le navigateur).
        refreshConnectedProviders()
    }

    fun refreshConnectedProviders() {
        viewModelScope.launch { refreshConnected() }
    }

    fun onVisibilityChange(type: CollectionType, visible: Boolean) {
        viewModelScope.launch { setVisible(type, visible) }
    }

    fun onSyncedChange(type: CollectionType, synced: Boolean) {
        viewModelScope.launch { setSynced(type, synced) }
    }

    fun onListModeChange(type: CollectionType, mode: ListMode) {
        viewModelScope.launch { setListMode(type, mode) }
    }

    fun onConnectSource(providerCode: String) {
        viewModelScope.launch {
            connectSource(providerCode).fold(
                onSuccess = { url -> _events.tryEmit(SourcesEvent.OpenUrl(url)) },
                onFailure = { _events.tryEmit(SourcesEvent.ConnectError) }
            )
        }
    }

    fun onDisconnectSource(providerCode: String) {
        viewModelScope.launch { disconnectSource(providerCode) }
    }
}

sealed interface SourcesEvent {
    /** Ouvrir l'URL d'autorisation OAuth dans le navigateur. */
    data class OpenUrl(val url: String) : SourcesEvent
    data object ConnectError : SourcesEvent
}

@Immutable
data class SourceUiState(
    val source: Source,
    val connected: Boolean
) {
    val requiresToken: Boolean get() = source.requiresToken
}

@Immutable
data class CollectionTypeManageUiState(
    val type: CollectionType,
    val visible: Boolean,
    /** `false` lorsqu'il s'agit du dernier type visible : on bloque sa désactivation. */
    val visibilityToggleEnabled: Boolean,
    val synced: Boolean,
    val supportsSerial: Boolean,
    val listMode: ListMode,
    val itemCount: Int,
    val sources: List<SourceUiState>
) {
    /** Sources du type qui nécessitent un jeton (exclut les sources publiques type BnF). */
    val tokenSources: List<SourceUiState> get() = sources.filter { it.requiresToken }
    val connectedTokenCount: Int get() = tokenSources.count { it.connected }
    val hasTokenSources: Boolean get() = tokenSources.isNotEmpty()
}
