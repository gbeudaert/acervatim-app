package app.acervatim.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.acervatim.domain.model.sync.SyncState
import java.time.Instant

/**
 * Conteneur de collection — entité miroir du `Collection` back (§5). Jusqu'ici l'app utilisait un
 * simple enum `CollectionType` comme « collection implicite » ; cette table matérialise une vraie
 * collection (une par type au démarrage, cf. seeding) pour que chaque item/nœud porte un
 * `collectionId` et que la synchro soit un mapping ~1:1.
 *
 * `type` = `typeCode` stable partagé avec le back (`vinyl`, `manga`, …).
 */
@Entity(
    tableName = "collections",
    indices = [
        Index(value = ["type"]),
        Index(value = ["remoteId"], unique = true)
    ]
)
data class CollectionEntity(
    @PrimaryKey val id: String,
    /** UUID serveur une fois synchronisée ; `null` tant que locale uniquement. */
    val remoteId: String?,
    val type: String,
    val name: String,
    val description: String?,
    val itemCount: Int,
    val syncState: SyncState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val serverUpdatedAt: Instant?
)
