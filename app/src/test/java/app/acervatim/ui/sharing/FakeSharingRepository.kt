package app.acervatim.ui.sharing

import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sharing.CollectionShare
import app.acervatim.domain.model.sharing.CreatedShare
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.domain.model.sharing.RedeemedShare
import app.acervatim.domain.model.sharing.SharableCollection
import app.acervatim.domain.repository.SharingRepository
import java.time.Instant

/**
 * Double de [SharingRepository] partagé par les tests des deux côtés du partage (propriétaire et
 * membre). Enregistre les appels plutôt que de les simuler finement : ce qu'on vérifie est *ce qui
 * a été demandé au serveur*, pas la façon dont il répondrait.
 */
internal class FakeSharingRepository : SharingRepository {

    var sharable: List<SharableCollection> = emptyList()
    var shares: List<CollectionShare> = emptyList()
    var received: List<ReceivedShare> = emptyList()

    var listCount = 0
    var receivedCount = 0
    val revokedShares = mutableListOf<String>()
    val revokedMembers = mutableListOf<Pair<String, String>>()
    val renamed = mutableListOf<Pair<String, String>>()
    val renamedReceived = mutableListOf<Pair<String, String>>()
    var lastCreate: CreateCall? = null
    var lastRedeem: Pair<String, String?>? = null

    var listResult: Result<List<CollectionShare>>? = null
    var receivedResult: Result<List<ReceivedShare>>? = null
    var createResult: Result<CreatedShare>? = null
    var redeemResult: Result<RedeemedShare>? = null
    var renameResult: Result<CollectionShare>? = null
    var renameReceivedResult: Result<ReceivedShare>? = null

    data class CreateCall(
        val entries: Map<String, Set<ItemStatus>>,
        val label: String?,
        val maxUses: Int,
        val expiresAt: Instant?
    )

    override suspend fun sharableCollections(): List<SharableCollection> = sharable

    override suspend fun shares(collectionId: String?): Result<List<CollectionShare>> {
        listCount++
        return listResult ?: Result.success(shares)
    }

    override suspend fun createShare(
        entries: Map<String, Set<ItemStatus>>,
        label: String?,
        maxUses: Int,
        expiresAt: Instant?
    ): Result<CreatedShare> {
        lastCreate = CreateCall(entries, label, maxUses, expiresAt)
        return createResult ?: Result.success(
            CreatedShare(
                share = CollectionShare(
                    id = "s1",
                    label = label,
                    maxUses = maxUses,
                    usedCount = 0,
                    expiresAt = expiresAt,
                    createdAt = Instant.parse("2026-08-22T10:00:00Z")
                ),
                code = "CODE-123"
            )
        )
    }

    override suspend fun renameShare(shareId: String, label: String): Result<CollectionShare> {
        renamed += shareId to label
        return renameResult ?: Result.success(shares.first { it.id == shareId }.copy(label = label))
    }

    override suspend fun revokeShare(shareId: String): Result<Unit> {
        revokedShares += shareId
        return Result.success(Unit)
    }

    override suspend fun revokeMember(shareId: String, memberUserId: String): Result<Unit> {
        revokedMembers += shareId to memberUserId
        return Result.success(Unit)
    }

    override suspend fun redeem(code: String, label: String?): Result<RedeemedShare> {
        lastRedeem = code to label
        return redeemResult ?: Result.success(
            RedeemedShare(
                share = ReceivedShare(
                    shareId = "s1",
                    label = label,
                    redeemedAt = Instant.parse("2026-08-22T10:00:00Z")
                ),
                alreadyMember = false
            )
        )
    }

    override suspend fun receivedShares(): Result<List<ReceivedShare>> {
        receivedCount++
        return receivedResult ?: Result.success(received)
    }

    override suspend fun renameReceivedShare(
        shareId: String,
        label: String
    ): Result<ReceivedShare> {
        renamedReceived += shareId to label
        return renameReceivedResult
            ?: Result.success(received.first { it.shareId == shareId }.copy(label = label))
    }
}
