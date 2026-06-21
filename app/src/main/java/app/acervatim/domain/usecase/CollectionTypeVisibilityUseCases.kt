package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Types de collection visibles dans le drawer / la bascule de collection.
 * Par défaut tous, un type masqué conserve ses données (cf. [SettingsRepository]).
 */
class ObserveVisibleCollectionTypesUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<Set<CollectionType>> = repository.observeVisibleCollectionTypes()
}

class SetCollectionTypeVisibleUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(type: CollectionType, visible: Boolean) {
        repository.setCollectionTypeVisible(type, visible)
    }
}
