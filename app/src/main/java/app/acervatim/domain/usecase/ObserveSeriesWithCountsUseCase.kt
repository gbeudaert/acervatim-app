package app.acervatim.domain.usecase

import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.model.normalizeArtistName
import app.acervatim.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveSeriesWithCountsUseCase @Inject constructor(
    private val repository: SeriesRepository
) {
    /**
     * Observe les séries d'un type de collection, filtrées par [query] sur le titre ou l'auteur.
     * Le filtre est insensible à la casse et aux accents (via [normalizeArtistName]), cohérent
     * avec la déduplication par clé naturelle. Le décompte des tomes reste calculé en SQL côté
     * DAO ; seul le filtre texte est appliqué en mémoire (volume de séries faible par collection).
     */
    operator fun invoke(
        collectionType: CollectionType,
        query: String = ""
    ): Flow<List<SeriesWithCounts>> {
        val normalizedQuery = normalizeArtistName(query)
        val source = repository.observeWithCountsByCollectionType(collectionType)
        if (normalizedQuery.isEmpty()) return source
        return source.map { list ->
            list.filter { swc ->
                normalizeArtistName(swc.series.title).contains(normalizedQuery) ||
                    normalizeArtistName(swc.series.originalAuthor.orEmpty()).contains(normalizedQuery)
            }
        }
    }
}
