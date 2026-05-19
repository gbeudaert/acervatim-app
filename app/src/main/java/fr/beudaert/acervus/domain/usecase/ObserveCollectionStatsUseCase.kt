package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveCollectionStatsUseCase @Inject constructor(
    private val repository: RecordRepository
) {
    /** `collectionType` null = stats globales toutes collections confondues. */
    operator fun invoke(collectionType: CollectionType? = null): Flow<CollectionStats> =
        combine(
            repository.observeOwnedCount(collectionType),
            repository.observeWishlistCount(collectionType)
        ) { owned, wishlist ->
            CollectionStats(ownedCount = owned, wishlistCount = wishlist)
        }
}

data class CollectionStats(
    val ownedCount: Int,
    val wishlistCount: Int
) {
    val totalCount: Int get() = ownedCount + wishlistCount
}
