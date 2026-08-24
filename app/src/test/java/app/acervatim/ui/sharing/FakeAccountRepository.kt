package app.acervatim.ui.sharing

import app.acervatim.domain.model.account.AccountInfo
import app.acervatim.domain.model.account.InvitationRedeemResult
import app.acervatim.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAccountRepository : AccountRepository {
    val info = MutableStateFlow<AccountInfo>(AccountInfo.Unknown)
    var refreshCount = 0

    override val accountInfo: Flow<AccountInfo> = info

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun redeemInvitation(code: String): Result<InvitationRedeemResult> =
        Result.success(InvitationRedeemResult(alreadyRedeemed = false, premiumGranted = false))
}
