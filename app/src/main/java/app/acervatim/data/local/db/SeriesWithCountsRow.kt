package app.acervatim.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.time.Instant

/**
 * Ligne JOIN agrégée d'une série avec ses compteurs de tomes.
 * Utilisé par la vue SERIAL pour afficher la tuile d'une série sans charger tous ses tomes.
 */
data class SeriesWithCountsRow(
    @Embedded val series: SeriesEntity,
    @ColumnInfo(name = "ownedVolumes") val ownedVolumes: Int,
    @ColumnInfo(name = "wishlistVolumes") val wishlistVolumes: Int,
    @ColumnInfo(name = "ignoredVolumes") val ignoredVolumes: Int,
    @ColumnInfo(name = "totalVolumesInLibrary") val totalVolumesInLibrary: Int,
    /** MAX des `lastPlayedAt` des tomes ; `null` si aucun tome écouté (cf. rubriques galerie). */
    @ColumnInfo(name = "lastPlayedAt") val lastPlayedAt: Instant? = null,
    /** SUM des `playCount` des tomes ; 0 si aucun. */
    @ColumnInfo(name = "playCount") val playCount: Int = 0
)
