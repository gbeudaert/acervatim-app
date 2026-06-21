package app.acervatim.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.sync.SyncState
import java.time.Instant

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["collectionType"]),
        Index(value = ["title"]),
        Index(value = ["collectionId"]),
        Index(value = ["remoteId"], unique = true),
        Index(value = ["syncState"]),
        Index(
            value = ["collectionType", "normalizedTitle", "normalizedAuthor"],
            unique = true,
            name = "index_series_natural_key"
        )
    ]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val collectionType: CollectionType,
    val title: String,
    val originalAuthor: String?,
    /**
     * Forme normalisée (lowercase + trim) de [title]. Sert exclusivement à porter
     * l'unique composite « clé naturelle » (collectionType, normalizedTitle, normalizedAuthor)
     * pour bloquer la création de doublons en SQL (Room ne supporte pas `COLLATE NOCASE`
     * directement dans un [Index]).
     */
    val normalizedTitle: String,
    /** Forme normalisée de [originalAuthor]. `""` si auteur null/vide. */
    val normalizedAuthor: String,
    val publicationStatus: String?,
    val totalVolumes: Int?,
    val synopsis: String?,
    val coverPath: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    // --- Métadonnées de synchronisation (le nœud miroir du back) ---
    val collectionId: String = "",
    /** UUID serveur du nœud une fois synchronisé ; `null` tant que local. */
    val remoteId: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val serverUpdatedAt: Instant? = null
)
