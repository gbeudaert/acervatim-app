package app.acervatim.domain

import app.acervatim.data.local.db.normalizeSeriesKey
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.model.normalizeEditionKey
import app.acervatim.domain.model.normalizePublisherKey
import app.acervatim.domain.repository.RecordRepository
import app.acervatim.domain.repository.SeriesRepository
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
                            it.status == app.acervatim.domain.model.ItemStatus.OWNED
                        },
                        wishlistVolumes = items.count {
                            it.status == app.acervatim.domain.model.ItemStatus.WISHLIST
                        },
                        ignoredVolumes = items.count {
                            it.status == app.acervatim.domain.model.ItemStatus.IGNORED
                        },
                        totalVolumesInLibrary = items.size
                    )
                }
        }
    }

    override fun observeCountByCollectionType(collectionType: CollectionType): Flow<Int> =
        state.asStateFlow().map { list -> list.count { it.collectionType == collectionType } }

    override suspend fun findByNaturalKey(
        collectionType: CollectionType,
        title: String,
        originalAuthor: String?,
        publisherFr: String?,
        edition: String?
    ): Series? {
        val nt = normalizeSeriesKey(title)
        val na = normalizeSeriesKey(originalAuthor)
        val np = normalizePublisherKey(publisherFr)
        val ne = normalizeEditionKey(edition)
        return state.value.firstOrNull { s ->
            s.collectionType == collectionType &&
                normalizeSeriesKey(s.title) == nt &&
                normalizeSeriesKey(s.originalAuthor) == na &&
                normalizePublisherKey(s.publisherFr) == np &&
                normalizeEditionKey(s.edition) == ne
        }
    }

    /** Identifiants serveur programmables (le domaine [Series] ne porte pas son `remoteId`). */
    val remoteIds: MutableMap<String, String> = mutableMapOf()

    override suspend fun findByRemoteId(remoteId: String): Series? =
        remoteIds[remoteId]?.let { localId -> state.value.firstOrNull { it.id == localId } }

    /** Lectures de [remoteIdOf] : le rattrapage ne doit en faire qu'une par vérification. */
    var remoteIdOfCalls: Int = 0

    override suspend fun remoteIdOf(seriesId: String): String? {
        remoteIdOfCalls++
        return remoteIds.entries.firstOrNull { it.value == seriesId }?.key
    }

    /** Échec programmable de [upsert], pour tester les filets d'erreur des appelants. */
    var failOnUpsert: Throwable? = null

    override suspend fun listAll(): List<Series> = state.value

    override suspend fun upsert(series: Series) {
        failOnUpsert?.let { throw it }
        state.update { current ->
            val idx = current.indexOfFirst { it.id == series.id }
            if (idx >= 0) current.toMutableList().apply { set(idx, series) }
            else current + series
        }
    }

    override suspend fun deleteById(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}
