package app.acervatim.ui.sharing.received

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Réglages en mémoire pour les tests de la vue partagée.
 *
 * Seul `duplicateAlbumsPerArtist` est réellement utilisé ici — c'est la préférence d'affichage que
 * la vue partagée hérite de l'utilisateur. Le reste rend les valeurs par défaut de l'app.
 */
internal class FakeSettingsRepository(
    duplicateAlbums: Boolean = false
) : SettingsRepository {

    val duplicateAlbumsPerArtist = MutableStateFlow(duplicateAlbums)

    override fun observeDuplicateAlbumsPerArtist(): Flow<Boolean> = duplicateAlbumsPerArtist

    override suspend fun setDuplicateAlbumsPerArtist(value: Boolean) {
        duplicateAlbumsPerArtist.value = value
    }

    override fun observeActiveCollectionType(): Flow<CollectionType> = flowOf(CollectionType.VINYL)

    override suspend fun setActiveCollectionType(value: CollectionType) = Unit

    override fun observeListMode(type: CollectionType): Flow<ListMode> = flowOf(type.defaultListMode)

    override suspend fun setListMode(type: CollectionType, mode: ListMode) = Unit

    override fun observeVisibleCollectionTypes(): Flow<Set<CollectionType>> =
        flowOf(CollectionType.entries.toSet())

    override suspend fun setCollectionTypeVisible(type: CollectionType, visible: Boolean) = Unit

    override fun observeSyncedCollectionTypes(): Flow<Set<CollectionType>> =
        flowOf(CollectionType.entries.toSet())

    override suspend fun setCollectionTypeSynced(type: CollectionType, synced: Boolean) = Unit
}
