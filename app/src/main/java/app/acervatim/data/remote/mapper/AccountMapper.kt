package app.acervatim.data.remote.mapper

import app.acervatim.data.remote.dto.MeResponse
import app.acervatim.data.remote.dto.PremiumDto
import app.acervatim.data.remote.dto.RedeemInvitationResponse
import app.acervatim.domain.model.account.InvitationRedeemResult
import app.acervatim.domain.model.account.PremiumSource
import app.acervatim.domain.model.account.PremiumStatus
import app.acervatim.domain.model.account.UserProfile
import java.time.Instant

/** Mapping DTO compte (`/v1/me`, invitations) → modèles domain. */
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

    fun RedeemInvitationResponse.toDomain(): InvitationRedeemResult =
        InvitationRedeemResult(alreadyRedeemed = alreadyRedeemed, premiumGranted = premiumGranted)
}
