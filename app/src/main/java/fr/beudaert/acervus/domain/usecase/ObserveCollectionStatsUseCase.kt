package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveCollectionStatsUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    operator fun invoke(): Flow<CollectionStats> =
        combine(repository.observeOwnedCount(), repository.observeWishlistCount()) { owned, wishlist ->
            CollectionStats(ownedCount = owned, wishlistCount = wishlist)
        }
}

data class CollectionStats(
    val ownedCount: Int,
    val wishlistCount: Int
) {
    val totalCount: Int get() = ownedCount + wishlistCount
}