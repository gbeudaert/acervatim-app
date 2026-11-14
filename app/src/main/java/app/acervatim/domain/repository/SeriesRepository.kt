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
     * Renvoie la série partageant la clé naturelle (`collectionType, title, originalAuthor,
     * publisherFr, edition`, insensible à la casse / accents ; éditeur et mention normalisés comme
     * le backend), null sinon. Sert au pré-check de doublon. `publisherFr`/`edition` à `null`
     * désignent une série sans édition connue — celles saisies à la main, ou créées avant la v8.
     */
    suspend fun findByNaturalKey(
        collectionType: CollectionType,
        title: String,
        originalAuthor: String?,
        publisherFr: String? = null,
        edition: String? = null
    ): Series?

    /**
     * Série locale rattachée au nœud serveur [remoteId], null si aucune. Sert à rapprocher les
     * nouveautés signalées par le serveur (`GET /v1/series-updates`, indexées par id de nœud).
     */
    suspend fun findByRemoteId(remoteId: String): Series?

    /**
     * Id du nœud serveur de la série [seriesId], `null` tant qu'elle n'a pas été synchronisée (ou si
     * elle n'existe pas). Une seule lecture pour comparer aux `nodeId` de `series-updates`, au lieu
     * d'une recherche par nœud.
     */
    suspend fun remoteIdOf(seriesId: String): String?

    /** Snapshot non-réactif de toutes les séries (utilisé par l'export backup). */
    suspend fun listAll(): List<Series>

    suspend fun upsert(series: Series)

    suspend fun deleteById(id: String)

    /** Supprime toutes les séries. Les FK des records (seriesId) basculent à NULL via ON DELETE SET NULL. */
    suspend fun deleteAll()
}
