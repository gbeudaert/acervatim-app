package fr.beudaert.acervus.domain.model

enum class CollectionType(val defaultListMode: ListMode, val supportsSerial: Boolean) {
    VINYL(defaultListMode = ListMode.FLAT, supportsSerial = false)
    // MANGA, LEGO_SET, BOARD_GAME, COMIC… arriveront aux sprints suivants.
}
