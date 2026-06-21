package app.acervatim.domain.model

enum class ItemStatus {
    /** Item possédé. */
    OWNED,

    /** Item désiré (wishlist). */
    WISHLIST,

    /**
     * Item explicitement ignoré dans une série (tome spinoff, hors-série non désiré…).
     * Utilisé en vue SERIAL uniquement à ce sprint ; en vue FLAT (vinyle) la valeur
     * reste valide mais l'UI ne l'expose pas comme cible de bascule.
     */
    IGNORED
}
