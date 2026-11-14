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
        // v8 : l'édition (éditeur + mention) entre dans la clé naturelle — deux éditions d'une même
        // série coexistent (arbitrage du 2026-08-26), Pika standard ≠ 2-en-1 France Loisirs.
        Index(
            value = [
                "collectionType",
                "normalizedTitle",
                "normalizedAuthor",
                "normalizedPublisher",
                "normalizedEdition"
            ],
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
     * l'unique composite « clé naturelle » (collectionType, normalizedTitle, normalizedAuthor,
     * normalizedPublisher, normalizedEdition) pour bloquer la création de doublons en SQL (Room
     * ne supporte pas `COLLATE NOCASE` directement dans un [Index]).
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
    // --- Édition (v8) — cf. Series.edition / Series.publisherFr ---
    val edition: String? = null,
    val publisherFr: String? = null,
    val mangaId: String? = null,
    /** Clé d'éditeur ([app.acervatim.domain.model.normalizePublisherKey]). `""` si inconnu. */
    val normalizedPublisher: String = "",
    /** Clé de mention d'édition ([app.acervatim.domain.model.normalizeEditionKey]). `""` = standard. */
    val normalizedEdition: String = "",
    // --- Métadonnées de synchronisation (le nœud miroir du back) ---
    val collectionId: String = "",
    /** UUID serveur du nœud une fois synchronisé ; `null` tant que local. */
    val remoteId: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val serverUpdatedAt: Instant? = null
)
