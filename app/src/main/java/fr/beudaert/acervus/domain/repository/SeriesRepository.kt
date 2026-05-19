package fr.beudaert.acervus.domain.repository

import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.Series
import fr.beudaert.acervus.domain.model.SeriesWithCounts
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {

    fun observeById(id: String): Flow<Series?>

    fun observeByCollectionType(collectionType: CollectionType): Flow<List<Series>>

    fun observeWithCountsByCollectionType(collectionType: CollectionType): Flow<List<SeriesWithCounts>>

    fun observeCountByCollectionType(collectionType: CollectionType): Flow<Int>

    suspend fun upsert(series: Series)

    suspend fun deleteById(id: String)
}
