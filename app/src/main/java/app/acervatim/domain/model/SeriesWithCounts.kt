package app.acervatim.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

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
    val totalVolumesInLibrary: Int,
    /**
     * Écoute la plus récente parmi les tomes de la série — `null` si aucun tome n'a jamais été
     * écouté. Agrégé en SQL : la vue séries ne charge pas les tomes.
     */
    val lastPlayedAt: Instant? = null,
    /** Somme des compteurs d'écoute des tomes. 0 = série jamais écoutée. */
    val playCount: Int = 0
)