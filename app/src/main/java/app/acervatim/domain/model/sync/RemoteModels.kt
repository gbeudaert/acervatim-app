package app.acervatim.domain.model.sync

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/**
 * Modèles « miroir du backend » (§5-§9). Ils représentent fidèlement le contrat
 * `Collection → Node / Item → Source`, avec les métadonnées de synchronisation
 * (`remoteId`, [SyncState], `serverUpdatedAt`). Ce sont eux que manipule le moteur de sync ;
 * l'UI continue de passer par la façade `Record` / `Series` (mappers dédiés).
 *
 * `unifiedData` est conservé en [JsonObject] brut : vérité curée dont la forme dépend du type
 * (profils §9). On ne la déstructure que pour l'affichage / le requêtage, via les mappers façade.
 */

@Immutable
data class RemoteCollection(
    val localId: String,
    val remoteId: String? = null,
    /** `typeCode` back (`vinyl`, `manga`, …). */
    val type: String,
    val name: String,
    val description: String? = null,
    val itemCount: Int = 0,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val updatedAt: Instant,
    val serverUpdatedAt: Instant? = null
)

@Immutable
data class RemoteSource(
    val provider: String,
    val externalId: String,
    val fetchedAt: Instant? = null
)

@Immutable
data class RemoteNode(
    val localId: String,
    val remoteId: String? = null,
    val collectionLocalId: String,
    val level: String,
    val isWishlist: Boolean = false,
    val ownedCount: Int = 0,
    val note: Int? = null,
    val comment: String? = null,
    val unifiedData: JsonObject = JsonObject(emptyMap()),
    val sources: List<RemoteSource> = emptyList(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val updatedAt: Instant,
    val serverUpdatedAt: Instant? = null
)

@Immutable
data class RemoteItem(
    val localId: String,
    val remoteId: String? = null,
    val collectionLocalId: String,
    val nodeLocalId: String? = null,
    val volume: Int? = null,
    val unifiedData: JsonObject = JsonObject(emptyMap()),
    val sources: List<RemoteSource> = emptyList(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val updatedAt: Instant,
    val serverUpdatedAt: Instant? = null
)
