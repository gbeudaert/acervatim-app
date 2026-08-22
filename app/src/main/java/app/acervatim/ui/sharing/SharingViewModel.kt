package app.acervatim.ui.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ShareScope
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.AccountRepository
import app.acervatim.domain.repository.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Ce que l'écran affiche à la place de la liste tant qu'elle n'est pas exploitable. */
sealed interface SharingContent {
    data object Loading : SharingContent

    /**
     * La collection n'a pas d'`id` serveur : jamais synchronisée. Il n'y a rien à partager — le
     * partage porte sur la collection distante, pas sur la base locale.
     */
    data object NotSynced : SharingContent

    /** Le chargement de la liste a échoué (réseau, serveur) — distinct d'une liste vide. */
    data object Failed : SharingContent

    data class Shares(val shares: List<CollectionShare>) : SharingContent
}

data class SharingUiState(
    val collectionType: CollectionType = CollectionType.VINYL,
    val content: SharingContent = SharingContent.Loading,
    /**
     * Émettre un partage est premium. `false` seulement quand le serveur a **dit** que le compte ne
     * l'est pas : profil inconnu (backend muet) ⇒ on laisse tenter, le 402 tranchera. Un ancien
     * premium garde de toute façon l'accès à la révocation.
     */
    val canCreate: Boolean = true,
    val busy: Boolean = false,
    /** Échec d'une action ponctuelle (création/révocation), affiché puis consommé. */
    val error: SharingFailure? = null
)

/**
 * Écran propriétaire : créer, lister et révoquer les partages d'une collection.
 *
 * **Le code d'un partage ne vit qu'ici, en mémoire** ([createdShare]) : ni `SavedStateHandle`, ni
 * DataStore, ni Room, ni log. Le serveur n'en stocke que le HMAC, il est irrécupérable après coup —
 * c'est le prix du modèle hors-bande, et l'UI doit le dire plutôt que de laisser croire l'inverse.
 */
@HiltViewModel
class SharingViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val accountRepository: AccountRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val collectionType: CollectionType =
        savedStateHandle.get<String>(COLLECTION_TYPE_ARG)
            ?.let { name -> CollectionType.entries.firstOrNull { it.name == name } }
            ?: CollectionType.VINYL

    private val _uiState = MutableStateFlow(SharingUiState(collectionType = collectionType))
    val uiState: StateFlow<SharingUiState> = _uiState.asStateFlow()

    /** Non nul juste après une création réussie, jusqu'à [consumeCreatedShare]. */
    private val _createdShare = MutableStateFlow<CreatedShare?>(null)
    val createdShare: StateFlow<CreatedShare?> = _createdShare.asStateFlow()

    private var collectionId: String? = null

    init {
        viewModelScope.launch {
            accountRepository.refresh()
            refreshPremium()
            load()
        }
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    fun createShare(scope: ShareScope, maxUses: Int, expiresAt: Instant?) {
        val id = collectionId ?: return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.createShare(id, scope, maxUses.coerceAtLeast(1), expiresAt)
                .onSuccess { created ->
                    _createdShare.value = created
                    reload(id)
                }
                .onFailure { failed(it) }
        }
    }

    fun revokeShare(shareId: String) {
        val id = collectionId ?: return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.revokeShare(shareId)
                .onSuccess { reload(id) }
                .onFailure { failed(it) }
        }
    }

    fun revokeMember(shareId: String, memberUserId: String) {
        val id = collectionId ?: return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.revokeMember(shareId, memberUserId)
                .onSuccess { reload(id) }
                .onFailure { failed(it) }
        }
    }

    /** Le code est oublié dès que l'écran de résultat est quitté : il ne sera plus jamais affiché. */
    fun consumeCreatedShare() {
        _createdShare.value = null
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun load() {
        _uiState.update { it.copy(content = SharingContent.Loading, error = null) }
        val id = sharingRepository.remoteCollectionId(collectionType)
        collectionId = id
        if (id == null) {
            _uiState.update { it.copy(content = SharingContent.NotSynced, busy = false) }
            return
        }
        reload(id)
    }

    private suspend fun reload(id: String) {
        sharingRepository.shares(id)
            .onSuccess { shares ->
                _uiState.update {
                    it.copy(content = SharingContent.Shares(shares), busy = false)
                }
            }
            .onFailure { error ->
                _uiState.update {
                    // Une liste déjà affichée ne disparaît pas parce qu'un rechargement échoue :
                    // seule l'erreur remonte, l'état précédent reste à l'écran.
                    val content =
                        if (it.content is SharingContent.Shares) it.content else SharingContent.Failed
                    it.copy(content = content, busy = false, error = error.toFailure())
                }
            }
    }

    private suspend fun failed(error: Throwable) {
        _uiState.update { it.copy(busy = false, error = error.toFailure()) }
        if (error.toFailure() == SharingFailure.PREMIUM_REQUIRED) {
            // Le premium a expiré entre l'affichage et l'action : réaligner l'écran sur le serveur.
            accountRepository.refresh()
            refreshPremium()
        }
    }

    private suspend fun refreshPremium() {
        val info = accountRepository.accountInfo.first()
        val canCreate = when (info) {
            is AccountInfo.Known -> info.profile.premium.isPremium
            AccountInfo.Unknown, AccountInfo.SignedOut -> true
        }
        _uiState.update { it.copy(canCreate = canCreate) }
    }

    private fun Throwable.toFailure(): SharingFailure =
        (this as? SharingException)?.failure ?: SharingFailure.UNKNOWN

    companion object {
        const val COLLECTION_TYPE_ARG: String = "collectionType"
    }
}
