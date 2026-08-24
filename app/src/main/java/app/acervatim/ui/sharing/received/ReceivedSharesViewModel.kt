package app.acervatim.ui.sharing.received

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce que l'écran affiche à la place de la liste tant qu'elle n'est pas exploitable. */
sealed interface ReceivedContent {
    data object Loading : ReceivedContent

    /** Le chargement a échoué (réseau, serveur) — distinct d'une liste vide. */
    data object Failed : ReceivedContent

    data class Shares(val shares: List<ReceivedShare>) : ReceivedContent
}

data class ReceivedSharesUiState(
    val content: ReceivedContent = ReceivedContent.Loading,
    val busy: Boolean = false,
    /** Échec d'une action ponctuelle (saisie de code, renommage), affiché puis consommé. */
    val error: SharingFailure? = null,
    /**
     * Vient de rejoindre un partage auquel on appartenait déjà. Ce n'est **pas** une erreur — le
     * redeem est idempotent — mais le dire évite de laisser croire qu'une place a été consommée.
     */
    val alreadyMember: Boolean = false
)

/**
 * Écran membre : rejoindre un partage avec un code, lister ceux qu'on a rejoints, les renommer.
 *
 * **Ne demande pas le premium** : c'est le point d'entrée principal d'un membre du foyer qui ne paie
 * pas. Le propriétaire paie le stockage, le membre lit.
 *
 * Le code saisi ne vit que le temps de l'appel : il n'est ni conservé dans l'état, ni journalisé.
 * Un partage rejoint se retrouve par la liste, pas par son code.
 */
@HiltViewModel
class ReceivedSharesViewModel @Inject constructor(
    private val sharingRepository: SharingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceivedSharesUiState())
    val uiState: StateFlow<ReceivedSharesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /**
     * Consomme un code. [label] est le nom que **je** donne au partage : le propriétaire ne le voit
     * pas, et le sien ne descend jamais jusqu'ici.
     */
    fun join(code: String, label: String?) {
        if (code.isBlank()) return
        _uiState.update { it.copy(busy = true, error = null, alreadyMember = false) }
        viewModelScope.launch {
            sharingRepository.redeem(code, label)
                .onSuccess { redeemed ->
                    _uiState.update { it.copy(alreadyMember = redeemed.alreadyMember) }
                    load()
                }
                .onFailure { failed(it) }
        }
    }

    /** Renomme, pour moi seul, un partage rejoint. Chaîne vide ⇒ le libellé est effacé. */
    fun rename(shareId: String, label: String) {
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            sharingRepository.renameReceivedShare(shareId, label)
                .onSuccess { load() }
                .onFailure { failed(it) }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumeAlreadyMember() {
        _uiState.update { it.copy(alreadyMember = false) }
    }

    private suspend fun load() {
        sharingRepository.receivedShares()
            .onSuccess { shares ->
                _uiState.update { it.copy(content = ReceivedContent.Shares(shares), busy = false) }
            }
            .onFailure { error ->
                _uiState.update {
                    // Une liste déjà affichée survit à un rechargement raté : seule l'erreur remonte.
                    val content =
                        if (it.content is ReceivedContent.Shares) it.content else ReceivedContent.Failed
                    it.copy(content = content, busy = false, error = error.toFailure())
                }
            }
    }

    private suspend fun failed(error: Throwable) {
        val failure = error.toFailure()
        _uiState.update { it.copy(busy = false, error = failure) }
        // Le partage visé a disparu (révoqué, membre éjecté) : la liste affichée ment, on la
        // resynchronise plutôt que de laisser agir sur une ligne fantôme.
        if (failure == SharingFailure.GONE) load()
    }

    private fun Throwable.toFailure(): SharingFailure =
        (this as? SharingException)?.failure ?: SharingFailure.UNKNOWN
}
