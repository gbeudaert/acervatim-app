package app.acervatim.domain.repository

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesWithCounts
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {

    fun observeById(id: String): Flow<Series?>

    fun observeByCollectionType(collectionType: CollectionType): Flow<List<Series>>

    fun observeWithCountsByCollectionType(collectionType: CollectionType): Flow<List<SeriesWithCounts>>

    fun observeCountByCollectionType(collectionType: CollectionType): Flow<Int>

    /**
     * Renvoie la série partageant la clé naturelle (`collectionType, title, originalAuthor`,
     * insensible à la casse / accents), null sinon. Sert au pré-check de doublon.
     */
    suspend fun findByNaturalKey(
        collectionType: CollectionType,
        title: String,
        originalAuthor: String?
    ): Series?

    /** Snapshot non-réactif de toutes les séries (utilisé par l'export backup). */
    suspend fun listAll(): List<Series>

    suspend fun upsert(series: Series)

    suspend fun deleteById(id: String)

    /** Supprime toutes les séries. Les FK des records (seriesId) basculent à NULL via ON DELETE SET NULL. */
    suspend fun deleteAll()
}
