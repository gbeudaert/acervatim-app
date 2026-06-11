package fr.beudaert.acervatim.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * Ligne JOIN agrégée d'une série avec ses compteurs de tomes.
 * Utilisé par la vue SERIAL pour afficher la tuile d'une série sans charger tous ses tomes.
 */
data class SeriesWithCountsRow(
    @Embedded val series: SeriesEntity,
    @ColumnInfo(name = "ownedVolumes") val ownedVolumes: Int,
    @ColumnInfo(name = "wishlistVolumes") val wishlistVolumes: Int,
    @ColumnInfo(name = "ignoredVolumes") val ignoredVolumes: Int,
    @ColumnInfo(name = "totalVolumesInLibrary") val totalVolumesInLibrary: Int
)
