package fr.beudaert.acervus.domain.model

enum class ListMode {
    /** Liste plate, un item par ligne. */
    FLAT,

    /** Liste de séries, chaque entrée ouvre l'écran "Détail série" et sa grille de tomes. */
    SERIAL
}
