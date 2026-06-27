package app.acervatim.domain.model

/**
 * Résultat de recherche externe (via le backend `GET /v1/search`) projeté en modèle domaine, prêt à
 * pré-remplir un formulaire d'ajout. Volontairement neutre vis-à-vis du type de collection : les
 * champs non pertinents pour un type donné restent simplement `null`/vides.
 *
 * Le couple `{ source, sourceId }` est conservé pour un futur rattachement de source (Sprints 12/13).
 */
data class ExternalMatch(
    val source: String,
    val sourceId: String,
    val title: String,
    val creators: List<String> = emptyList(),
    val year: Int? = null,
    val coverUrl: String? = null,
    val description: String? = null
)
