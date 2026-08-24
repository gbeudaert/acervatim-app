package app.acervatim.domain.repository

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.RecordSort
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
     * Densité d'affichage (liste / grille / galerie) choisie pour un [CollectionType] donné.
     * Axe **orthogonal** au mode de référencement : les deux se combinent, et chacun est persisté
     * par type. Renvoie [defaultDisplayMode] du type si aucun choix n'a été persisté.
     */
    fun observeDisplayMode(type: CollectionType): Flow<DisplayMode>

    suspend fun setDisplayMode(type: CollectionType, mode: DisplayMode)

    /**
     * Tri de la collection choisi pour un [CollectionType] donné. Persisté comme les deux modes
     * ci-dessus, et pour la même raison : trier ses vinyles par année et ses mangas par numéro
     * n'a rien de contradictoire. Renvoie [RecordSort.DEFAULT] si rien n'a été choisi, ou si la
     * valeur stockée n'est plus lisible (champ renommé entre deux versions).
     */
    fun observeSort(type: CollectionType): Flow<RecordSort>

    suspend fun setSort(type: CollectionType, sort: RecordSort)

    /**
     * Types de collection que l'utilisateur a choisi d'afficher (drawer + bascule de collection).
     * Par défaut tous les [CollectionType] sont visibles. Un type masqué conserve ses données :
     * il est simplement retiré du menu. Au moins un type reste toujours visible.
     */
    fun observeVisibleCollectionTypes(): Flow<Set<CollectionType>>

    suspend fun setCollectionTypeVisible(type: CollectionType, visible: Boolean)

    /**
     * Types de collection inclus dans la synchronisation cloud. Par défaut **tous** les
     * [CollectionType] sont synchronisés (clé absente ⇒ tout synchronisé). Un type retiré n'émet
     * plus aucune requête de sync pour ses données ; contrairement à la visibilité, un ensemble
     * **vide est autorisé** (l'utilisateur peut tout garder local).
     */
    fun observeSyncedCollectionTypes(): Flow<Set<CollectionType>>

    suspend fun setCollectionTypeSynced(type: CollectionType, synced: Boolean)
}
