package fr.beudaert.acervatim.domain.model

import androidx.compose.runtime.Immutable

/**
 * Une "ligne d'affichage" dans la liste collection.
 * - En mode "duplicate" (ON), un disque à N artistes produit N entries (une par artiste).
 * - En mode "primary" (OFF), un disque produit une seule entry (artiste principal).
 */
@Immutable
data class RecordArtistEntry(
    val record: Record,
    val displayedArtist: Artist,
    val featuringArtists: List<Artist>
) {
    val isPrimary: Boolean get() = displayedArtist.id == record.primaryArtist?.id
}
