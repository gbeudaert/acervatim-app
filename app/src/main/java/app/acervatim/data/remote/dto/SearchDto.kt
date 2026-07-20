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
    val ongoing: Boolean = false
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
    val description: String? = null
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
    val edition: String? = null
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
