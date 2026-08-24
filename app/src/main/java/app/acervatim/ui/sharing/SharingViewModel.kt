package app.acervatim.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.SharableCollection
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
     * Aucune collection n'a d'`id` serveur : rien n'a jamais été synchronisé. Il n'y a rien à
     * partager — le partage porte sur la copie serveur, pas sur la base locale.
     */
    data object NotSynced : SharingContent

    /** Le chargement de la liste a échoué (réseau, serveur) — distinct d'une liste vide. */
    data object Failed : SharingContent

    data class Shares(val shares: List<CollectionShare>) : SharingContent
}

data class SharingUiState(
    val content: SharingContent = SharingContent.Loading,
    /** Collections synchronisées, seules candidates au partage. Alimente le sélecteur de création. */
    val sharable: List<SharableCollection> = emptyList(),
    /**
     * Émettre un partage est premium. `false` seulement quand le serveur a **dit** que le compte ne
     * l'est pas : profil inconnu (backend muet) ⇒ on laisse tenter, le 402 tranchera. Un ancien
     * premium garde de toute façon l'accès à la révocation et au renommage.
     */
    val canCreate: Boolean = true,
    val busy: Boolean = false,
    /** Échec d'une action ponctuelle (création/renommage/révocation), affiché puis consommé. */
    val error: SharingFailure? = null
)

/**
 * Écran propriétaire : créer, lister, renommer et révoquer **ses** partages.
 *
 * L'écran n'est plus ancré sur une collection : depuis le sprint backend S5, un partage en réunit
 * plusieurs sous un seul code. Il vit donc au niveau du compte, et la collection n'est plus qu'un
 * critère de composition.
 *
 * **Le code d'un partage ne vit qu'ici, en mémoire** ([createdShare]) : ni `SavedStateHandle`, ni
 * DataStore, ni Room, ni log. Le serveur n'en stocke que le HMAC, il est irrécupérable après coup —
 * c'est le prix du modèle hors-bande, et l'UI doit le dire plutôt que de laisser croire l'inverse.
 */
@HiltViewModel
class SharingViewModel @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharingUiState())
    val uiState: StateFlow<SharingUiState> = _uiState.asStateFlow()

    /** Non nul juste après une création réussie, jusqu'à [consumeCreatedShare]. */
    private val _createdShare = MutableStateFlow<CreatedShare?>(null)
    val createdShare: StateFlow<CreatedShare?> = _createdShare.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.refresh()
            refreshPremium()
            load()
        }
    }

    fun retry() {
        viewModelScope.launch {
            // Seul un retry volontaire efface l'erreur affichée : `load()` sert aussi de resynchro
            // *déclenchée par* une erreur, et n'a donc pas à effacer ce qu'il vient expliquer.
            _uiState.update { it.copy(error = null) }
            load()
        }
    }

    /**
     * [entries] : une collection → les statuts qu'elle expose. Les ensembles vides sont écartés ici
     * plutôt que renvoyés en erreur : une collection dont on décoche tout n'est pas partagée, ce qui
     * est exactement ce que le geste veut dire. Si tout est vide, il n'y a rien à créer.
     */
    fun createShare(
        entries: Map<String, Set<ItemStatus>>,
        label: String?,
        maxUses: Int,
        expiresAt: Instant?
    ) {
        val kept = entries.filterValues { it.isNotEmpty() }
        if (kept.isEmpty()) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.createShare(kept, label, maxUses.coerceAtLeast(1), expiresAt)
                .onSuccess { created ->
                    _createdShare.value = created
                    reload()
                }
                .onFailure { failed(it) }
        }
    }

    /** Renomme un partage. Chaîne vide ⇒ le libellé est effacé (le serveur normalise en `null`). */
    fun renameShare(shareId: String, label: String) {
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.renameShare(shareId, label)
                .onSuccess { reload() }
                .onFailure { failed(it) }
        }
    }

    fun revokeShare(shareId: String) {
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.revokeShare(shareId)
                .onSuccess { reload() }
                .onFailure { failed(it) }
        }
    }

    fun revokeMember(shareId: String, memberUserId: String) {
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.revokeMember(shareId, memberUserId)
                .onSuccess { reload() }
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
        _uiState.update { it.copy(content = SharingContent.Loading) }
        val sharable = sharingRepository.sharableCollections()
        _uiState.update { it.copy(sharable = sharable) }
        if (sharable.isEmpty()) {
            // Rien de synchronisé : la liste distante serait vide de toute façon, et l'écran doit
            // expliquer pourquoi au lieu d'afficher « aucun partage ».
            _uiState.update { it.copy(content = SharingContent.NotSynced, busy = false) }
            return
        }
        reload()
    }

    private suspend fun reload() {
        sharingRepository.shares()
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
        val failure = error.toFailure()
        _uiState.update { it.copy(busy = false, error = failure) }
        when (failure) {
            SharingFailure.PREMIUM_REQUIRED -> {
                // Le premium a expiré entre l'affichage et l'action : réaligner l'écran sur le serveur.
                accountRepository.refresh()
                refreshPremium()
            }
            // Le partage visé n'existe plus (révoqué depuis un autre appareil, membre déjà éjecté) :
            // la liste affichée ment, on la resynchronise plutôt que de laisser agir dessus.
            SharingFailure.GONE -> reload()
            // Un `remoteId` local est périmé : c'est le sélecteur de collections qu'il faut refaire,
            // pas seulement la liste des partages — d'où `load()` et non `reload()`.
            SharingFailure.COLLECTION_GONE -> load()
            else -> Unit
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
}
