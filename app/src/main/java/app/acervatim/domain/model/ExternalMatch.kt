package app.acervatim.domain.model

/**
 * Résultat de recherche externe (via le backend `GET /v1/search`) projeté en modèle domaine, prêt à
 * pré-remplir un formulaire d'ajout. Volontairement neutre vis-à-vis du type de collection : les
 * champs non pertinents pour un type donné restent simplement `null`/vides.
 *
 * Le couple `{ source, sourceId }` est conservé pour un futur rattachement de source (Sprints 12/13).
 *
 * Les champs `seriesTitleFr`/`volumeNumber`/`edition`/`totalVolumes` ne sont peuplés que pour le manga
 * (lus depuis `metadata.scannedTome` du pivot BnF→MAL) ; ils servent à créer/rattacher la série et à
 * piloter l'import complet via `edition-mapping`. Restent `null` pour les autres types.
 */
data class ExternalMatch(
    val source: String,
    val sourceId: String,
    val title: String,
    val creators: List<String> = emptyList(),
    val year: Int? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    /** Genre principal (vinyle : 1re valeur de `metadata.genres` Discogs) ; `null` hors vinyle. */
    val genre: String? = null,
    /** Format physique du disque (vinyle : dérivé de `metadata.format` Discogs) ; `null` sinon. */
    val format: AcervatimFormat? = null,
    /** Vitesse de gravure (vinyle : `metadata.recordingSpeed` normalisé serveur) ; `null` sinon. */
    val recordingSpeed: RecordingSpeed? = null,
    /** Titre FR de la série (notice BnF `scannedTome.titleFr`) — clé de `edition-mapping`. */
    val seriesTitleFr: String? = null,
    /** N° de tome scanné (notice BnF `scannedTome.volume`). */
    val volumeNumber: Int? = null,
    /** Mention d'édition (notice BnF `scannedTome.edition`), null = édition standard. */
    val edition: String? = null,
    /** Éditeur FR du tome (notice BnF `scannedTome.publisherFr`). */
    val publisherFr: String? = null,
    /**
     * Plage de tomes de l'édition source couverte par ce tome (`scannedTome.sourceVolumeRange`,
     * ex "1-3" pour une Colossale). Sert à afficher « contient les tomes 1 à 3 ». null = 1:1.
     */
    val sourceVolumeRange: String? = null,
    /** Nombre total de tomes de la série (MAL `num_volumes`), si connu. */
    val totalVolumes: Int? = null
)
