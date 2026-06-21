package app.acervatim.domain.model

/**
 * Normalise un ISBN-10 ou ISBN-13 en supprimant tirets, espaces et caractères de séparation.
 * La lettre `X` (chiffre de contrôle ISBN-10) est conservée en majuscule. Renvoie `null` si
 * la saisie est vide ou ne contient aucun chiffre.
 *
 * On reste tolérant : ce n'est pas un validateur strict (pas de contrôle de checksum), juste
 * une normalisation pour que `"978-2-7234-1234-5"` et `"9782723412345"` soient traités comme
 * la même clé par l'index unique de [Record.isbn].
 */
fun normalizeIsbn(value: String?): String? {
    if (value == null) return null
    val cleaned = buildString {
        for (c in value) {
            when {
                c.isDigit() -> append(c)
                c == 'x' || c == 'X' -> append('X')
            }
        }
    }
    return cleaned.takeIf { it.isNotEmpty() }
}
