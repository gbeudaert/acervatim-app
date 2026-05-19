package fr.beudaert.acervus.domain.usecase

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.repository.RecordRepository
import fr.beudaert.acervus.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Stats agrégées pour un [CollectionType] — consommé par le drawer de switch de collection. */
data class CollectionTypeStats(
    val collectionType: CollectionType,
    val recordCount: Int,
    val seriesCount: Int
) {
    /**
     * Compteur affiché dans le drawer : nb de séries pour les types SERIAL par défaut
     * (où la liste principale est une liste de séries), nb d'items pour les autres.
     */
    val displayCount: Int
        get() = if (collectionType.supportsSerial) seriesCount else recordCount
}

class ObserveCollectionTypeStatsUseCase @Inject constructor(
    private val recordRepository: RecordRepository,
    private val seriesRepository: SeriesRepository
) {
    operator fun invoke(): Flow<List<CollectionTypeStats>> {
        val perTypeFlows: List<Flow<CollectionTypeStats>> = CollectionType.entries.map { type ->
            combine(
                recordRepository.observeTotalCount(type),
                if (type.supportsSerial) seriesRepository.observeCountByCollectionType(type) else flowOf(0)
            ) { recordCount, seriesCount ->
                CollectionTypeStats(type, recordCount, seriesCount)
            }
        }
        return combine(perTypeFlows) { it.toList() }
    }
}
