package app.acervatim.domain.model

/**
 * Clés normalisées de l'**identité d'une édition** manga : le couple (éditeur, mention d'édition).
 *
 * La mention `205$a` seule ne suffit pas : sur *L'attaque des titans*, l'édition « standard »
 * réunit Pika et les 2-en-1 France Loisirs, aucun des deux n'ayant de mention d'édition (cf.
 * `acervatim-docs/etude-leviers-recherche-manga.md` § 3). Les deux clés entrent donc dans la clé
 * naturelle d'une série, et doivent se normaliser **exactement comme le backend** les compare —
 * sinon le scan (éditeur brut BnF « Pika éd. ») et la recherche (libellé « Pika ») créeraient deux
 * séries pour la même édition.
 */

/**
 * Mots génériques d'un nom d'éditeur, sans valeur distinctive : « Pika éd. », « Pika édition » et
 * « Pika » sont le même éditeur ; « Pika roman » reste une autre marque. Copie de
 * `GENERIC_PUBLISHER_WORDS` (`acervatim-backend/src/common/sources/bnf/edition-records.ts`).
 */
private val GENERIC_PUBLISHER_WORDS = setOf("ed", "eds", "edition", "editions", "editeur", "editeurs")

/** Mots génériques d'une mention d'édition (« Éd. colossale » ≡ « Edition colossale »). */
private val GENERIC_EDITION_WORDS = setOf("edition", "ed")

/** Séparateurs de mots d'un nom d'éditeur : `/[\s,;:()[\]/-]+/` côté backend. */
private val PUBLISHER_SEPARATORS = Regex("[\\s,;:()\\[\\]/-]+")

/** Ce qu'un mot d'éditeur garde après pliage : `/[^a-z0-9@]+/g` retiré côté backend. */
private val PUBLISHER_TOKEN_STRIP = Regex("[^a-z0-9@]+")

private val NON_ALNUM = Regex("[^a-z0-9]+")

/**
 * Clé d'éditeur — portage **à l'identique** de `normalizePublisher` du backend : découpage sur les
 * séparateurs ci-dessus, chaque mot plié (sans accents, minuscules) puis réduit à `[a-z0-9@]`, mots
 * génériques retirés. Conséquences voulues, alignées sur le serveur : l'apostrophe ne sépare pas
 * (« L'Harmattan » → `lharmattan`), le trait d'union si (« Delcourt-Tonkam » → `delcourt tonkam`).
 * `""` si l'éditeur est inconnu — même convention que `normalizedAuthor`, pour que l'index unique
 * tienne (SQLite traite NULL != NULL).
 */
fun normalizePublisherKey(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    return raw.split(PUBLISHER_SEPARATORS)
        .map { normalizeArtistName(it).replace(PUBLISHER_TOKEN_STRIP, "") }
        .filter { it.isNotEmpty() && it !in GENERIC_PUBLISHER_WORDS }
        .joinToString(" ")
}

/** Clé de mention d'édition : « Éd. colossale » → `colossale` ; `null`/standard → `""`. */
fun normalizeEditionKey(raw: String?): String =
    normalizeArtistName(raw.orEmpty())
        .replace(NON_ALNUM, " ")
        .split(' ')
        .filter { it.isNotBlank() && it !in GENERIC_EDITION_WORDS }
        .joinToString(" ")
