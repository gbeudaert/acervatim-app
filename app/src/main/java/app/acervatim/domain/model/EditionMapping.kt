package app.acervatim.domain.model

/**
 * Énumération d'une édition manga « toute la série d'un coup » (backend
 * `GET /v1/search/edition-mapping`, source BnF). Projette le DTO en modèle domaine neutre, prêt à
 * piloter l'import d'une série (création des tomes manquants).
 */
data class EditionMapping(
    /** Titre FR de la série (nettoyé, sans n° de tome). */
    val titleFr: String,
    /** Mention d'édition (205, ex « Éd. colossale »), ou null pour l'édition standard. */
    val edition: String?,
    /** Nombre de tomes de cette édition (dérivé côté serveur). */
    val tomeCount: Int,
    /** Tomes ordonnés par n° d'édition. */
    val tomes: List<EditionTome>,
    /** Au moins une notice en cours de publication → mapping potentiellement partiel. */
    val ongoing: Boolean
)

/** Un tome d'une édition, vu par la BnF. */
data class EditionTome(
    /** 200$h — n° de tome dans l'édition. */
    val editionVolume: Int,
    /** 010$a — ISBN du tome (null si absent de la notice). */
    val isbn: String?,
    /** 200$a — titre FR du tome. */
    val titleFr: String?,
    /**
     * 454$h — plage de tomes de l'édition source couverte par ce tome (ex "1-3" pour une
     * Colossale). Sert à afficher « contient les tomes 1 à 3 ». null = correspondance 1:1.
     */
    val sourceVolumeRange: String? = null,
    /**
     * Jaquette du tome (résolue par ISBN via Google Books, côté serveur). Peuplée **depuis le cache
     * serveur uniquement** : `null` tant qu'aucune résolution `GET /v1/search/cover` n'a réchauffé
     * le cache pour cet ISBN. L'import résout alors à la demande (cf. [ExternalSearchRepository]).
     */
    val coverUrl: String? = null,
    /**
     * Résumé propre au tome (330$a BnF, sinon repli Google Books côté serveur), ou null. Jamais le
     * résumé de série. Stocké par tome à l'import (cf. [MangaExtras.KEY_TOME_SYNOPSIS]).
     */
    val description: String? = null
)
