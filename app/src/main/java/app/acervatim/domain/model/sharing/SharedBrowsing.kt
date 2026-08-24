package app.acervatim.domain.model.sharing

import app.acervatim.domain.model.ItemStatus

/**
 * Ce qu'un **membre** lit du **détail** d'un élément partagé.
 *
 * La vue *liste*, elle, n'a pas de modèle propre : une collection partagée est reconstruite en
 * `Record`/`Series` du domaine et rendue par le même corps de liste que les collections locales
 * (`ui/list/CollectionListBody.kt`). Il n'y a de modèle spécifique que là où la forme diffère
 * réellement — ici, parce qu'un membre ne reçoit qu'une partie des données perso.
 *
 * Rien de tout ceci n'est **jamais persisté** — ni Room, ni export, ni sauvegarde ZIP. Une
 * collection partagée est lue en ligne et disparaît si le propriétaire révoque.
 */

/** Un fait affichable du détail, nommé plutôt que libellé : la traduction reste dans les ressources. */
enum class SharedFactKey { CREATORS, GENRE, LABEL, FORMAT, RELEASE_DATE, VOLUME }

data class SharedFact(val key: SharedFactKey, val value: String)

/**
 * Détail d'un élément partagé.
 *
 * [rating] et [lastPlayedAt] sont les seules données perso qui descendent jusqu'au membre : S4 réduit
 * le `userData` d'un membre à `status`, `rating` et `lastPlayedAt`. `purchasePrice`, la note libre et
 * le commentaire de série sont **absents du JSON**, pas vides — l'écran doit donc les traiter comme
 * inexistants et ne rien afficher, plutôt que d'afficher un champ vide qui suggérerait une lacune.
 */
data class SharedItemDetail(
    val id: String,
    val title: String?,
    val coverUrl: String?,
    val volume: Int?,
    val status: ItemStatus?,
    val rating: Int?,
    /** Date ISO telle que reçue ; l'écran la met en forme. */
    val lastPlayedAt: String?,
    val facts: List<SharedFact>
)
