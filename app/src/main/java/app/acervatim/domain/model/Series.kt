package app.acervatim.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class Series(
    val id: String,
    val collectionType: CollectionType,
    val title: String,
    val originalAuthor: String? = null,
    val publicationStatus: String? = null,
    val totalVolumes: Int? = null,
    val synopsis: String? = null,
    val coverPath: String? = null,
    /**
     * Jaquette distante (URL provider), pendant de [Record.coverRemoteUrl].
     *
     * Champ **domaine seulement** : Room ne le stocke pas, il reste donc `null` pour les séries
     * locales. Il sert aux séries reconstruites depuis le réseau — une collection reçue en partage,
     * qui n'a par définition aucun fichier local.
     */
    val coverRemoteUrl: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
