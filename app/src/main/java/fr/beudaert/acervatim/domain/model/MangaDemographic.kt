package fr.beudaert.acervatim.domain.model

/**
 * Démographie cible d'une série manga, stockée dans `extras["demographic"]`.
 * Valeur libre acceptée : on essaie d'abord de mapper la chaîne vers un [MangaDemographic]
 * connu, sinon on retombe sur [OTHER] (et la valeur brute reste lisible dans les extras).
 */
enum class MangaDemographic {
    SHONEN, SHOJO, SEINEN, JOSEI, KODOMO, OTHER;

    companion object {
        fun fromExtras(value: String?): MangaDemographic? = value
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER
            }
    }
}
