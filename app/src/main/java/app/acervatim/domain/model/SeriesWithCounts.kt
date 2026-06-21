package app.acervatim.domain.model

import androidx.compose.runtime.Immutable

/**
 * Représentation d'une série dans la vue SERIAL : la série elle-même + son décompte de tomes
 * par statut. Utilisé pour afficher la tuile sans charger l'intégralité de la grille de tomes.
 */
@Immutable
data class SeriesWithCounts(
    val series: Series,
    val ownedVolumes: Int,
    val wishlistVolumes: Int,
    val ignoredVolumes: Int,
    val totalVolumesInLibrary: Int
)
