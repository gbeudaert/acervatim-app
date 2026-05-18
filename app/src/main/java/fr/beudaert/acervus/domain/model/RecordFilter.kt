package fr.beudaert.acervus.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class RecordFilter(
    val query: String = "",
    val status: StatusFilter = StatusFilter.ALL,
    val genre: String? = null,
    val format: AcervusFormat? = null,
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

    fun toRecordStatus(): RecordStatus? = when (this) {
        ALL -> null
        OWNED_ONLY -> RecordStatus.OWNED
        WISHLIST_ONLY -> RecordStatus.WISHLIST
    }
}