package app.acervatim.data.remote.mapper

import app.acervatim.data.remote.dto.MeResponse
import app.acervatim.data.remote.dto.PremiumDto
import app.acervatim.data.remote.dto.RedeemInvitationResponse
import app.acervatim.data.remote.mapper.AccountMapper.toDomain
import app.acervatim.domain.model.account.PremiumSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AccountMapperTest {

    @Test
    fun `me response maps profile premium and createdAt`() {
        val me = MeResponse(
            userId = "u1",
            createdAt = "2026-01-15T10:00:00Z",
            premium = PremiumDto(isPremium = true, source = "subscription", expiresAt = 1_750_000_000_000L)
        )

        val profile = me.toDomain()

        assertEquals("u1", profile.userId)
        assertEquals(Instant.parse("2026-01-15T10:00:00Z"), profile.createdAt)
        assertTrue(profile.premium.isPremium)
        assertEquals(PremiumSource.SUBSCRIPTION, profile.premium.source)
        assertEquals(Instant.ofEpochMilli(1_750_000_000_000L), profile.premium.expiresAt)
    }

    @Test
    fun `premium sources map to enum with unknown fallback`() {
        assertEquals(PremiumSource.GRANT, PremiumDto(source = "grant").toDomain().source)
        assertEquals(PremiumSource.SUBSCRIPTION, PremiumDto(source = "subscription").toDomain().source)
        assertEquals(PremiumSource.NONE, PremiumDto(source = "none").toDomain().source)
        assertEquals(PremiumSource.UNKNOWN, PremiumDto(source = "lifetime").toDomain().source)
    }

    @Test
    fun `null expiresAt maps to null instant`() {
        assertNull(PremiumDto(isPremium = true, source = "grant", expiresAt = null).toDomain().expiresAt)
    }

    @Test
    fun `redeem response maps flags`() {
        val result = RedeemInvitationResponse(alreadyRedeemed = false, premiumGranted = true).toDomain()
        assertFalse(result.alreadyRedeemed)
        assertTrue(result.premiumGranted)
    }
}
