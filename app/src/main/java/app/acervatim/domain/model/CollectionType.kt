package app.acervatim.domain.model

enum class CollectionType(
    val defaultListMode: ListMode,
    val supportsSerial: Boolean,
    /** Code stable partagé avec le backend (`type` du contrat, §5/§13). */
    val typeCode: String
) {
    VINYL(defaultListMode = ListMode.FLAT, supportsSerial = false, typeCode = "vinyl"),
    MANGA(defaultListMode = ListMode.SERIAL, supportsSerial = true, typeCode = "manga");
    // movie, book, game (côté back) arriveront aux sprints suivants.

    companion object {
        /** Résout un `typeCode` back vers un [CollectionType] connu, ou `null` (type non géré localement). */
        fun fromTypeCode(code: String): CollectionType? = entries.firstOrNull { it.typeCode == code }
    }
}
