package app.acervatim.domain.model

/**
 * Clés des champs manga stockés dans [Record.extras].
 *
 * On reste sur des `Map<String, String>` à dessein : c'est l'extension naturelle pour les
 * spécificités d'un type de collection (cf. §4 de `docs/contexte-global.md`). Les helpers ci-dessous
 * évitent les fautes de frappe à l'usage et permettent de centraliser la valeur des clés.
 */
object MangaExtras {
    // ISBN n'est plus dans extras : promu en colonne first-class `Record.isbn` (unique en base).
    const val KEY_PUBLISHER_FR: String = "publisherFr"
    const val KEY_ORIGINAL_AUTHOR: String = "originalAuthor"
    const val KEY_DEMOGRAPHIC: String = "demographic"
    const val KEY_PAGE_COUNT: String = "pageCount"
}

/** Lecture d'un entier optionnel depuis les extras (renvoie null si manquant ou mal formé). */
internal fun Map<String, String>.readInt(key: String): Int? =
    this[key]?.takeIf { it.isNotBlank() }?.toIntOrNull()

/** Lecture d'une chaîne non vide depuis les extras (renvoie null si manquante ou vide). */
internal fun Map<String, String>.readString(key: String): String? =
    this[key]?.takeIf { it.isNotBlank() }
