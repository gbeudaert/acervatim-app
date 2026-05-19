package fr.beudaert.acervus.domain

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.model.SeriesWithCounts
import fr.beudaert.acervus.domain.repository.RecordRepository
import fr.beudaert.acervus.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake en mémoire utilisé par les use cases de séries.
 * Pour les compteurs par série [observeWithCountsByCollectionType], on accepte (en option) un
 * [RecordRepository] afin de calculer le décompte de tomes en croisant avec les Records.
 */
class FakeSeriesRepository(
    private val recordRepository: RecordRepository? = null
) : SeriesRepository {

    private val state = MutableStateFlow<List<Series>>(emptyList())

    override fun observeById(id: String): Flow<Series?> =
        state.asStateFlow().map { list -> list.firstOrNull { it.id == id } }

    override fun observeByCollectionType(collectionType: CollectionType): Flow<List<Series>> =
        state.asStateFlow().map { list ->
            list.filter { it.collectionType == collectionType }
                .sortedBy { it.title.lowercase() }
        }

    override fun observeWithCountsByCollectionType(
        collectionType: CollectionType
    ): Flow<List<SeriesWithCounts>> {
        val recordsFlow = recordRepository?.observeAll()
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
        return combine(
            state.asStateFlow(),
            recordsFlow
        ) { series, records ->
            series.filter { it.collectionType == collectionType }
                .sortedBy { it.title.lowercase() }
                .map { s ->
                    val items = records.filter { it.seriesId == s.id }
                    SeriesWithCounts(
                        series = s,
                        ownedVolumes = items.count {
                            it.status == fr.beudaert.acervus.domain.model.ItemStatus.OWNED
                        },
                        wishlistVolumes = items.count {
                            it.status == fr.beudaert.acervus.domain.model.ItemStatus.WISHLIST
                        },
                        ignoredVolumes = items.count {
                            it.status == fr.beudaert.acervus.domain.model.ItemStatus.IGNORED
                        },
                        totalVolumesInLibrary = items.size
                    )
                }
        }
    }

    override fun observeCountByCollectionType(collectionType: CollectionType): Flow<Int> =
        state.asStateFlow().map { list -> list.count { it.collectionType == collectionType } }

    override suspend fun upsert(series: Series) {
        state.update { current ->
            val idx = current.indexOfFirst { it.id == series.id }
            if (idx >= 0) current.toMutableList().apply { set(idx, series) }
            else current + series
        }
    }

    override suspend fun deleteById(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }
}
