package app.acervatim.domain.repository

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ListMode
import kotlinx.coroutines.flow.Flow

/**
 * Préférences utilisateur persistantes (DataStore).
 */
interface SettingsRepository {

    /**
     * Lorsque `true`, un album multi-artistes apparaît une fois par artiste dans la liste
     * collection (sous chaque lettre du sticky header alphabétique). Lorsque `false`,
     * il n'apparaît que sous son artiste principal.
     */
    fun observeDuplicateAlbumsPerArtist(): Flow<Boolean>

    suspend fun setDuplicateAlbumsPerArtist(value: Boolean)

    /**
     * Type de collection actuellement affiché dans la liste principale. Persisté pour restaurer
     * le dernier type ouvert au prochain lancement.
     */
    fun observeActiveCollectionType(): Flow<CollectionType>

    suspend fun setActiveCollectionType(value: CollectionType)

    /**
     * Mode de référencement (FLAT / SERIAL) choisi pour un [CollectionType] donné.
     * Renvoie [CollectionType.defaultListMode] si aucun choix n'a été persisté pour ce type.
     */
    fun observeListMode(type: CollectionType): Flow<ListMode>

    suspend fun setListMode(type: CollectionType, mode: ListMode)

    /**
     * Types de collection que l'utilisateur a choisi d'afficher (drawer + bascule de collection).
     * Par défaut tous les [CollectionType] sont visibles. Un type masqué conserve ses données :
     * il est simplement retiré du menu. Au moins un type reste toujours visible.
     */
    fun observeVisibleCollectionTypes(): Flow<Set<CollectionType>>

    suspend fun setCollectionTypeVisible(type: CollectionType, visible: Boolean)
}
