package app.acervatim.data.repository

import app.acervatim.data.local.db.SeriesDao
import app.acervatim.data.local.db.normalizeSeriesKey
import app.acervatim.data.local.db.toDomain
import app.acervatim.data.local.db.toEntity
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.model.normalizeEditionKey
import app.acervatim.domain.model.normalizePublisherKey
import app.acervatim.domain.model.sync.SyncState
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val seriesDao: SeriesDao,
    private val collectionResolver: LocalCollectionResolver
) : SeriesRepository {

    override fun observeById(id: String): Flow<Series?> =
        seriesDao.observeById(id).map { it?.toDomain() }

    override fun observeByCollectionType(collectionType: CollectionType): Flow<List<Series>> =
        seriesDao.observeByCollectionType(collectionType).map { list -> list.map { it.toDomain() } }

    override fun observeWithCountsByCollectionType(
        collectionType: CollectionType
    ): Flow<List<SeriesWithCounts>> =
        seriesDao.observeWithCountsByCollectionType(collectionType)
            .map { list -> list.map { it.toDomain() } }

    override fun observeCountByCollectionType(collectionType: CollectionType): Flow<Int> =
        seriesDao.observeCountByCollectionType(collectionType)

    override suspend fun findByNaturalKey(
        collectionType: CollectionType,
        title: String,
        originalAuthor: String?,
        publisherFr: String?,
        edition: String?
    ): Series? = seriesDao.findByNaturalKey(
        collectionType = collectionType,
        normalizedTitle = normalizeSeriesKey(title),
        normalizedAuthor = normalizeSeriesKey(originalAuthor),
        normalizedPublisher = normalizePublisherKey(publisherFr),
        normalizedEdition = normalizeEditionKey(edition)
    )?.toDomain()

    override suspend fun findByRemoteId(remoteId: String): Series? =
        seriesDao.getByRemoteId(remoteId)?.toDomain()

    override suspend fun remoteIdOf(seriesId: String): String? =
        seriesDao.getEntityById(seriesId)?.remoteId

    override suspend fun listAll(): List<Series> =
        seriesDao.listAll().map { it.toDomain() }

    override suspend fun upsert(series: Series) {
        val collectionId = collectionResolver.collectionIdFor(series.collectionType)
        val existing = seriesDao.getEntityById(series.id)
        val syncState = if (existing?.remoteId != null) SyncState.DIRTY else SyncState.LOCAL_ONLY
        seriesDao.upsert(
            series.toEntity(
                collectionId = existing?.collectionId?.takeIf { it.isNotBlank() } ?: collectionId,
                remoteId = existing?.remoteId,
                syncState = syncState,
                serverUpdatedAt = existing?.serverUpdatedAt
            )
        )
    }

    override suspend fun deleteById(id: String) {
        seriesDao.deleteById(id)
    }

    override suspend fun deleteAll() {
        seriesDao.deleteAll()
    }
}
