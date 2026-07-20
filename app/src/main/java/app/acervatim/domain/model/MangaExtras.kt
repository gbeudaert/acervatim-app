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

    /** Mention d'édition (205 BnF, ex "Éd. colossale"). Absente = édition standard. */
    const val KEY_EDITION: String = "edition"

    /**
     * Plage de tomes de l'édition source couverte par ce tome (454$h, ex "1-3").
     * Présent surtout sur les éditions compilées (Colossale, Perfect…). Cf. [sourceVolumeLabel].
     */
    const val KEY_SOURCE_VOLUME_RANGE: String = "sourceVolumeRange"

    /**
     * Résumé **propre au tome** (330$a BnF, sinon repli Google Books). Distinct du synopsis de
     * série ([Series.synopsis]) : affiché sur le détail du tome, vide si aucune source par tome.
     */
    const val KEY_TOME_SYNOPSIS: String = "tomeSynopsis"

    /**
     * Libellé lisible d'une plage `454$h` : "1-3" → "1 à 3", "5" → "5". null si vide.
     * (Le texte complet « contient les tomes … » est assemblé côté UI, pour rester localisable.)
     */
    fun sourceVolumeLabel(range: String?): String? {
        val cleaned = range?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = Regex("""^(\d+)\s*-\s*(\d+)$""").find(cleaned)
        if (match != null) {
            val (from, to) = match.destructured
            return "$from à $to"
        }
        return cleaned
    }
}

/** Lecture d'un entier optionnel depuis les extras (renvoie null si manquant ou mal formé). */
internal fun Map<String, String>.readInt(key: String): Int? =
    this[key]?.takeIf { it.isNotBlank() }?.toIntOrNull()

/** Lecture d'une chaîne non vide depuis les extras (renvoie null si manquante ou vide). */
internal fun Map<String, String>.readString(key: String): String? =
    this[key]?.takeIf { it.isNotBlank() }
