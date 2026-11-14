package app.acervatim.domain.model

/**
 * Candidat d'une **recherche de série manga par titre** (`GET /v1/search?type=manga&q=`), servie par
 * MangaDex sans jeton. L'utilisateur choisit parmi ces candidats : c'est lui qui départage, pas un
 * score (« Frieren » renvoie aussi un crossover homonyme — cf. `spec-macro-import-serie-manga.md`).
 *
 * Chaque champ sert à **choisir en connaissance de cause** : jaquette, titres FR/romaji, auteurs,
 * année, statut et nombre de tomes distinguent deux candidats visuellement proches.
 */
data class SeriesCandidate(
    /** Provider du résultat : `mangadex` (nominal) ou `mal` (repli serveur, jeton MAL requis). */
    val source: String,
    val sourceId: String,
    /**
     * Id MangaDex de l'œuvre — la clé de `GET /v1/search/editions` et la jointure directe des
     * jaquettes. `null` pour un résultat du repli MAL, qui ne peut donc pas lister ses éditions.
     */
    val mangaId: String?,
    /** Titre d'affichage choisi par le serveur (FR si connu, sinon romaji). */
    val title: String,
    val titleFr: String?,
    val titleRomaji: String?,
    val authors: List<String>,
    val year: Int?,
    val coverUrl: String?,
    /** Synopsis (FR si connu, sinon EN). */
    val synopsis: String?,
    /** Statut de parution **de l'œuvre originale** (`ongoing`, `completed`, `hiatus`, `cancelled`). */
    val status: String?,
    /** Nombre de tomes de l'œuvre originale, si connu (pas celui de l'édition française). */
    val volumeCount: Int?,
    val malId: String?,
    val anilistId: String?
)

/**
 * Une **édition française** d'une série (`GET /v1/search/editions`, source BnF). Ce qui la distingue
 * d'une autre édition de la même œuvre doit être lisible sur la ligne : éditeur, mention, nombre de
 * tomes, années.
 *
 * [seriesTitleFr] + [edition] + [publisher] sont exactement les paramètres de `edition-mapping` et
 * d'`import-jobs` : on les renvoie tels quels, sans les retoucher.
 */
data class SeriesEditionOption(
    /** Titre de série tel que la BnF le catalogue — clé `title` de `edition-mapping`. */
    val seriesTitleFr: String,
    /**
     * Libellé lisible de l'éditeur (ex « Pika ») — clé `publisher` de `edition-mapping`. `null`
     * quand aucune notice du groupe n'en porte : l'écran affiche « Éditeur inconnu », et
     * `edition-mapping` repart sans éditeur (le serveur retient alors le dominant).
     */
    val publisher: String?,
    /** Mention d'édition brute (205$a), `null` pour l'édition standard. */
    val edition: String?,
    /** Édition d'art (Colossale, Perfect…) : autre visuel ET autre découpage que la standard. */
    val isArtEdition: Boolean,
    val tomeCount: Int,
    val firstYear: String?,
    val lastYear: String?,
    /** Au moins une notice « en cours » : l'édition gagnera probablement des tomes. */
    val ongoing: Boolean,
    /** Les tomes compilent ceux d'une autre édition (« contient les tomes 1 à 3 »). */
    val hasSourceVolumeRange: Boolean,
    val sampleIsbn: String?
)

/**
 * Nouveaux tomes d'une série **synchronisée**, détectés par le job serveur hebdomadaire
 * (`GET /v1/series-updates`, premium). [nodeId] est l'id **serveur** du nœud : l'app le rapproche de
 * sa série locale par `remoteId`.
 */
data class SeriesUpdate(
    val nodeId: String,
    val title: String,
    val edition: String?,
    val publisherFr: String?,
    /** Tomes connus de l'édition absents des items de ce nœud, déjà filtrés côté serveur. */
    val newTomes: List<EditionTome>
)
