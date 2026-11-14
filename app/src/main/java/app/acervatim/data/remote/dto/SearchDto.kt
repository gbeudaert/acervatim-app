package app.acervatim.data.remote.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

/**
 * Résultat de recherche externe unifiée (`GET /v1/search`, §8). Le couple `{ source, sourceId }`
 * sert ensuite de `{ provider, externalId }` pour créer un item/nœud.
 *
 * Le curseur de `/search` est **opaque non-UUID** (numéro/URL de page provider).
 */
@Serializable
data class UnifiedItemDto(
    val source: String,
    val sourceId: String,
    val mediaType: String,
    val title: String,
    val creators: List<String> = emptyList(),
    val releaseDate: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val metadata: JsonObject? = null,
    val rawData: JsonElement? = null
)

/**
 * Énumération d'une édition manga (`GET /v1/search/edition-mapping`, source BnF) : « récupérer toute
 * la série d'un coup ». Cf. `bnf.types.ts#EditionMapping` côté backend.
 */
@Serializable
data class EditionMappingDto(
    val titleFr: String,
    val edition: String? = null,
    val tomeCount: Int,
    val tomes: List<EditionTomeDto> = emptyList(),
    val recordsScanned: Int = 0,
    val ongoing: Boolean = false,
    /** Éditeur retenu (demandé, sinon dominant) — absent d'un serveur antérieur à SD1. */
    val publisher: String? = null,
    /**
     * `false` si l'énumération BnF s'est arrêtée en route (troncature, page SRU en échec). Absent
     * d'un serveur antérieur à SD3 : `true`, l'ancien comportement (on croyait la liste complète).
     */
    val complete: Boolean = true
)

@Serializable
data class EditionTomeDto(
    val editionVolume: Int,
    val sourceVolumeRange: String? = null,
    val isbn: String? = null,
    val titleFr: String? = null,
    /**
     * Jaquette du tome (résolue par ISBN via Google Books côté serveur). Peuplée **depuis le cache
     * serveur uniquement** lors de l'énumération : `null` tant que la jaquette n'a pas été résolue
     * via `GET /v1/search/cover`. Cf. `bnf.types.ts#EditionTomeWithCover`.
     */
    val coverUrl: String? = null,
    /**
     * Résumé **propre au tome** : 330$a BnF si présent, sinon repli Google Books côté serveur, sinon
     * `null`. Jamais le résumé de série (celui-ci reste au niveau série). Cf. `EditionTomeWithCover`.
     */
    val description: String? = null,
    /** Création de la notice BnF (`100$a`), `YYYY-MM-DD` ; `null` si illisible ou serveur ancien. */
    val noticeCreatedAt: String? = null
)

/** Réponse de `GET /v1/search/cover` : jaquette résolue par ISBN (`null` si aucune trouvée). */
@Serializable
data class CoverResponseDto(
    val coverUrl: String? = null
)

/**
 * Corps de `POST /v1/search/import-jobs` : lance l'import **asynchrone** d'une édition complète
 * (énumération BnF + jaquettes) en tâche de fond, hors du cycle requête-réponse du scan (cf. incident
 * 0.5.3). Mêmes champs que `edition-mapping`. Cf. `edition-import.controller.ts` (backend).
 */
@Serializable
data class CreateImportJobRequest(
    val title: String,
    val edition: String? = null,
    /**
     * Éditeur de l'édition (SD1) : il entre dans l'id déterministe du job, sans quoi la standard
     * Pika et la 2-en-1 France Loisirs partageraient un même job. Omis quand `null`.
     */
    val publisher: String? = null,
    /** Id MangaDex : jointure directe des jaquettes par tome, sans rapprochement par titre. */
    val mangaId: String? = null
)

/**
 * Réponse de `GET /v1/search/editions?mangaId=` : les éditions françaises (BnF) d'une série
 * MangaDex choisie, groupées par (série, éditeur, mention). Cf. contrat SD1.
 */
@Serializable
data class SeriesEditionsDto(
    val mangaId: String? = null,
    val editions: List<SeriesEditionDto> = emptyList(),
    /**
     * `false` : la BnF n'a pas répondu en entier (pages manquantes) — la liste peut être tronquée.
     * Champ additif, absent d'un serveur antérieur : `true` par défaut.
     */
    val complete: Boolean = true
)

@Serializable
data class SeriesEditionDto(
    val seriesTitleFr: String,
    /** `null` quand aucune notice du groupe ne porte d'éditeur (contrat : `string | null`). */
    val publisher: String? = null,
    val edition: String? = null,
    val isArtEdition: Boolean = false,
    val tomeCount: Int = 0,
    val firstYear: String? = null,
    val lastYear: String? = null,
    val ongoing: Boolean = false,
    val hasSourceVolumeRange: Boolean = false,
    val sampleIsbn: String? = null
)

/**
 * Réponse de `GET /v1/series-updates` (premium) : nouveaux tomes des séries synchronisées, détectés
 * par le job serveur hebdomadaire (SD3). `nodeId` = id **serveur** du nœud.
 */
@Serializable
data class SeriesUpdatesDto(
    val updates: List<SeriesUpdateDto> = emptyList()
)

@Serializable
data class SeriesUpdateDto(
    val nodeId: String,
    val title: String,
    val edition: String? = null,
    val publisherFr: String? = null,
    val newTomes: List<NewTomeDto> = emptyList()
)

@Serializable
data class NewTomeDto(
    val editionVolume: Int,
    val isbn: String? = null,
    val titleFr: String? = null,
    val noticeCreatedAt: String? = null,
    val coverUrl: String? = null
)

/** Réponse `202` de `POST /v1/search/import-jobs` : id (déterministe, dédup inter-user) + état initial. */
@Serializable
data class ImportJobDto(
    val jobId: String,
    /** `queued` | `running` | `done` | `failed` (cf. `ApiJobState` backend). */
    val state: String
)

/** Statut renvoyé par `GET /v1/search/import-jobs/:id` (`404` si l'id est inconnu). */
@Serializable
data class ImportJobStatusDto(
    val jobId: String,
    val state: String,
    /** Avancement publié par le worker ; `null` tant que rien n'est encore mesuré. */
    val progress: ImportJobProgressDto? = null
)

@Serializable
data class ImportJobProgressDto(
    /** `enumerating` (énumération BnF) | `covers` (résolution des jaquettes). */
    val phase: String,
    val done: Int,
    val total: Int
)
