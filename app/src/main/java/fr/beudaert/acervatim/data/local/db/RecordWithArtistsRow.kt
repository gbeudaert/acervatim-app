package fr.beudaert.acervatim.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * Ligne brute renvoyée par les requêtes JOIN `records × record_artists × artists`.
 * Une ligne par (disque, artiste) ; le disque est répété pour chaque artiste.
 */
data class RecordWithArtistsRow(
    @Embedded val record: RecordEntity,
    @ColumnInfo(name = "artist_id") val artistId: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "artist_position") val position: Int
)
