package app.acervatim.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.RecordingSpeed
import app.acervatim.domain.model.sync.SyncState
import java.time.Instant

@Entity(
    tableName = "records",
    indices = [
        Index(value = ["title"]),
        Index(value = ["status"]),
        Index(value = ["barcode"], unique = true),
        Index(value = ["isbn"], unique = true),
        Index(value = ["collectionType"]),
        Index(value = ["seriesId"]),
        Index(value = ["collectionId"]),
        Index(value = ["remoteId"], unique = true),
        Index(value = ["syncState"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class RecordEntity(
    @PrimaryKey val id: String,
    val collectionType: CollectionType,
    val seriesId: String?,
    val volumeNumber: Int?,
    val title: String,
    val genre: String?,
    val year: Int?,
    val label: String?,
    val format: AcervatimFormat?,
    val recordingSpeed: RecordingSpeed?,
    val country: String?,
    val purchasePrice: Double?,
    val rating: Int?,
    val coverPath: String?,
    val coverRemoteUrl: String?,
    val barcode: String?,
    /**
     * Identifiant naturel pour les livres / mangas (ISBN-10 ou ISBN-13). Unique en base
     * pour bloquer les doublons. Distinct de `barcode` car certains tomes ont un EAN-13
     * = ISBN-13 (même valeur) mais d'autres ont uniquement l'ISBN au dos sans code-barres.
     * Stocké normalisé (sans tirets ni espaces) côté domain pour faciliter le matching.
     */
    val isbn: String?,
    val discogsId: Long?,
    val status: ItemStatus,
    val lastPlayedAt: Instant?,
    /**
     * Champs spécifiques à un type de collection (ex. manga : publisherFr, demographic, pageCount, …).
     * Sérialisés en JSON via [Converters]. L'ISBN, autrefois ici, est désormais en colonne
     * first-class pour l'indice unique.
     */
    val extras: Map<String, String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    // --- Métadonnées de synchronisation (miroir backend) ---
    /** Collection conteneuse (FK logique vers `collections.id`). */
    val collectionId: String = "",
    /** UUID serveur de l'item une fois synchronisé ; `null` tant que local. */
    val remoteId: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val serverUpdatedAt: Instant? = null
)
