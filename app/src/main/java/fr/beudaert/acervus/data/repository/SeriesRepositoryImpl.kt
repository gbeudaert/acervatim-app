package fr.beudaert.acervus.data.repository

import fr.beudaert.acervus.data.local.db.SeriesDao
import fr.beudaert.acervus.data.local.db.toDomain
import fr.beudaert.acervus.data.local.db.toEntity
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.model.SeriesWithCounts
import fr.beudaert.acervus.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val seriesDao: SeriesDao
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

    override suspend fun upsert(series: Series) {
        seriesDao.upsert(series.toEntity())
    }

    override suspend fun deleteById(id: String) {
        seriesDao.deleteById(id)
    }
}
