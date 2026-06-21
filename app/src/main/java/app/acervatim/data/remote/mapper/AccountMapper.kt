package app.acervatim.data.remote.mapper

import app.acervatim.data.remote.dto.MeResponse
import app.acervatim.data.remote.dto.PremiumDto
import app.acervatim.data.remote.dto.QuotaBucketDto
import app.acervatim.data.remote.dto.QuotaResponse
import app.acervatim.data.remote.dto.RedeemInvitationResponse
import app.acervatim.domain.model.account.InvitationRedeemResult
import app.acervatim.domain.model.account.PremiumSource
import app.acervatim.domain.model.account.PremiumStatus
import app.acervatim.domain.model.account.QuotaBucket
import app.acervatim.domain.model.account.QuotaInfo
import app.acervatim.domain.model.account.UserProfile
import java.time.Instant

/** Mapping DTO compte (`/v1/me`, `/v1/me/quota`, invitations) → modèles domain. */
object AccountMapper {

    fun MeResponse.toDomain(): UserProfile =
        UserProfile(
            userId = userId,
            createdAt = RemoteDtoMapper.parseInstant(createdAt),
            premium = premium.toDomain()
        )

    fun PremiumDto.toDomain(): PremiumStatus =
        PremiumStatus(
            isPremium = isPremium,
            source = when (source) {
                "grant" -> PremiumSource.GRANT
                "subscription" -> PremiumSource.SUBSCRIPTION
                "none" -> PremiumSource.NONE
                else -> PremiumSource.UNKNOWN
            },
            expiresAt = expiresAt?.let(Instant::ofEpochMilli)
        )

    fun QuotaResponse.toDomain(): QuotaInfo =
        QuotaInfo(collections = collections.toDomain(), items = items.toDomain())

    fun QuotaBucketDto.toDomain(): QuotaBucket = QuotaBucket(used = used, max = max)

    fun RedeemInvitationResponse.toDomain(): InvitationRedeemResult =
        InvitationRedeemResult(alreadyRedeemed = alreadyRedeemed, premiumGranted = premiumGranted)
}
