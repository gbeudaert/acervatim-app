package app.acervatim.domain.repository

import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.account.InvitationRedeemResult
import kotlinx.coroutines.flow.Flow

/**
 * Profil/quota du compte backend (`/v1/me`, `/v1/me/quota`). Lecture seule côté app : le statut
 * premium et les quotas sont décidés par le serveur. Aucun gating local ne doit dépendre d'une
 * réponse de ce repository — backend muet ⇒ [AccountInfo.Unknown], jamais bloquant.
 */
interface AccountRepository {

    /** Flow froid : [AccountInfo.SignedOut] sans session, sinon dernier snapshot connu. */
    val accountInfo: Flow<AccountInfo>

    /**
     * Recharge `/v1/me` puis `/v1/me/quota`. Ne lève jamais : toute erreur dégrade en
     * [AccountInfo.Unknown] (ou `quota = null` si seul le quota échoue). No-op si déconnecté.
     */
    suspend fun refresh()

    /** `POST /v1/invitations/redeem`, puis [refresh] si le premium a été accordé. */
    suspend fun redeemInvitation(code: String): Result<InvitationRedeemResult>
}
