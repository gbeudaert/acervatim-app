package fr.beudaert.acervatim.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class RecordFilter(
    val query: String = "",
    val collectionType: CollectionType? = null,
    val listMode: ListMode = ListMode.FLAT,
    val status: StatusFilter = StatusFilter.ALL,
    val genre: String? = null,
    val format: AcervatimFormat? = null,
    val notPlayedSinceSixMonths: Boolean = false
) {
    val isAnyAdvancedFilterActive: Boolean
        get() = status != StatusFilter.ALL ||
                genre != null ||
                format != null ||
                notPlayedSinceSixMonths
}

enum class StatusFilter {
    ALL,
    OWNED_ONLY,
    WISHLIST_ONLY;

    fun toItemStatus(): ItemStatus? = when (this) {
        ALL -> null
        OWNED_ONLY -> ItemStatus.OWNED
        WISHLIST_ONLY -> ItemStatus.WISHLIST
    }
}
