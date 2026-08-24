package app.acervatim.domain.model

import androidx.compose.runtime.Immutable

/**
 * Critère de tri de la collection.
 *
 * Délibérément **à côté** de [RecordFilter] et non dedans : un filtre restreint *ce qu'on voit*,
 * un tri ordonne *ce qui reste*. Les deux ne se réinitialisent pas ensemble non plus — remettre
 * les filtres à zéro ne doit pas reprendre la main sur l'ordre choisi — et le tri se persiste
 * par type de collection là où un filtre est passager.
 *
 * Le **genre n'est pas un critère de tri** : trier par genre revient à grouper, ce qui est un
 * besoin différent (et l'app est mono-genre, cf. la dette assumée du sprint AD2). Il reste un
 * filtre.
 */
@Immutable
data class RecordSort(
    val field: RecordSortField = RecordSortField.ARTIST,
    val direction: SortDirection = SortDirection.ASCENDING
) {
    companion object {
        /** Ordre historique de la liste, avant qu'il devienne choisissable. */
        val DEFAULT = RecordSort(RecordSortField.ARTIST, SortDirection.ASCENDING)
    }
}

enum class RecordSortField {
    /** Artiste principal, puis titre — l'ordre historique. */
    ARTIST,
    TITLE,
    YEAR,
    LAST_PLAYED,
    /** Date d'ajout à la collection (`createdAt`). */
    ADDED,
    RATING
}

enum class SortDirection {
    ASCENDING,
    DESCENDING;

    fun toggled(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
}

/**
 * Sens naturel d'un champ quand on vient de le choisir : on veut les dernières écoutes, les
 * meilleures notes et les ajouts récents **en tête**, mais les artistes et les titres de A à Z.
 * Imposer partout le même sens obligerait à un deuxième geste juste après le premier.
 */
val RecordSortField.naturalDirection: SortDirection
    get() = when (this) {
        RecordSortField.ARTIST, RecordSortField.TITLE -> SortDirection.ASCENDING
        RecordSortField.YEAR, RecordSortField.LAST_PLAYED,
        RecordSortField.ADDED, RecordSortField.RATING -> SortDirection.DESCENDING
    }

/**
 * Un en-tête alphabétique collant n'a de sens que si l'ordre *est* alphabétique. Sur un tri par
 * année ou par dernière écoute, il annoncerait un classement qui n'existe pas : la liste n'en
 * pose alors aucun.
 */
val RecordSortField.hasLetterSections: Boolean
    get() = this == RecordSortField.ARTIST || this == RecordSortField.TITLE
