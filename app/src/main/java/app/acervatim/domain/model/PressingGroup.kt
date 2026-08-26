package app.acervatim.domain.model

/**
 * Pressages d'un même album, regroupés pour la sélection.
 *
 * Une recherche par code-barres identifie **une** édition ; une recherche par texte en renvoie
 * couramment vingt à trente pour le même album (pays, année, label, rééditions). Affichée à plat,
 * la liste est inexploitable : deux lignes identiques à l'écran, deux disques différents en base.
 * Le regroupement rend la structure visible — un album, ses pressages — et c'est
 * [ExternalMatch.masterId] (Discogs `master_id`, commun à tous les pressages d'un album) qui le
 * permet.
 *
 * @property key identité du groupe, stable d'une recomposition à l'autre (master, ou le pressage
 *   lui-même quand il n'en déclare pas).
 * @property pressings au moins un élément, triés du plus ancien au plus récent (année inconnue en
 *   dernier) : entre deux pressages du même album, la date est le repère naturel.
 */
data class PressingGroup(
    val key: String,
    val title: String,
    val creators: List<String>,
    val coverUrl: String?,
    val pressings: List<ExternalMatch>
) {
    /** Un groupe d'un seul pressage se choisit directement, sans passer par la liste. */
    val isSingle: Boolean get() = pressings.size == 1
}

/**
 * Regroupe des résultats de recherche par album.
 *
 * L'ordre des groupes suit celui reçu du serveur (pertinence Discogs) : le premier pressage
 * rencontré fixe la place du groupe **et** sa vignette (titre, artistes, jaquette), car c'est le
 * plus pertinent — le tri par année ne s'applique qu'à l'intérieur du groupe.
 *
 * Un pressage sans `masterId` forme son propre groupe : sans master déclaré, rien ne prouve qu'il
 * s'agit du même album qu'un autre, et regrouper par titre rapprocherait des homonymes.
 */
fun List<ExternalMatch>.groupPressings(): List<PressingGroup> {
    val byKey = LinkedHashMap<String, MutableList<ExternalMatch>>()
    for (match in this) {
        val key = match.masterId?.takeIf { it.isNotBlank() }
            ?.let { "master:$it" }
            ?: "release:${match.source}:${match.sourceId}"
        byKey.getOrPut(key) { mutableListOf() } += match
    }
    return byKey.map { (key, pressings) ->
        val representative = pressings.first()
        PressingGroup(
            key = key,
            title = representative.title,
            creators = representative.creators,
            coverUrl = pressings.firstNotNullOfOrNull { it.coverUrl?.takeIf(String::isNotBlank) },
            pressings = pressings.sortedWith(compareBy(nullsLast<Int>()) { it.year })
        )
    }
}
