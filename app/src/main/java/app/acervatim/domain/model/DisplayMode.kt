package app.acervatim.domain.model

/**
 * Densité visuelle de la collection — **axe orthogonal** à [ListMode].
 *
 * [ListMode] décrit la *hiérarchie* (liste plate de tomes/disques vs liste de séries) ;
 * [DisplayMode] décrit la *façon de montrer* ce niveau-là. Les deux se combinent : une
 * collection de séries peut s'afficher en liste, en grille ou en galerie exactement comme une
 * collection plate. En faire une troisième valeur de [ListMode] mélangerait les deux axes et
 * enfermerait la galerie dans un seul type de collection.
 */
enum class DisplayMode {
    /** Une ligne par item : vignette, titre, sous-titre. La vue historique. */
    LIST,

    /** Tuiles de jaquettes, sans rubriques : toute la collection d'un bloc. */
    GRID,

    /** Grandes jaquettes groupées en rubriques d'écoute (derniers, plus écoutés, oubliés). */
    GALLERY
}

/**
 * Densité par défaut d'un type de collection, tant que l'utilisateur n'a rien choisi : une
 * collection de séries s'ouvre en grille (c'est ce que la vue séries a toujours fait), une
 * collection plate en liste. Sans ça, introduire [DisplayMode] aurait silencieusement retiré
 * sa grille à la vue manga existante.
 */
val CollectionType.defaultDisplayMode: DisplayMode
    get() = if (defaultListMode == ListMode.SERIAL) DisplayMode.GRID else DisplayMode.LIST
