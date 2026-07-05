package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Types de collection inclus dans la synchronisation cloud. Par défaut tous (cf.
 * [SettingsRepository.observeSyncedCollectionTypes]).
 */
class ObserveSyncedCollectionTypesUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<Set<CollectionType>> = repository.observeSyncedCollectionTypes()
}

class SetCollectionTypeSyncedUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(type: CollectionType, synced: Boolean) {
        repository.setCollectionTypeSynced(type, synced)
    }
}
