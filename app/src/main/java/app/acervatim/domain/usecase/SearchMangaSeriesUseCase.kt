package app.acervatim.domain.usecase

import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.SeriesCandidate
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.ExternalSearchRepository
import javax.inject.Inject

/**
 * Cherche une **série manga par titre** (`GET /v1/search?type=manga&q=`, servie par MangaDex sans
 * jeton) — la porte d'entrée de la wishlist : ajouter une série dont on ne possède aucun tome.
 *
 * Mêmes courts-circuits que [SearchExternalByTextUseCase], dans le même ordre : requête trop courte,
 * puis absence de session (la recherche transite par le serveur). Les candidats sortent **tels que
 * le serveur les ordonne** (par nombre de suivis) : c'est l'utilisateur qui départage, pas un score
 * local — « Frieren » renvoie aussi un crossover homonyme que seul un œil humain écarte à coup sûr.
 */
class SearchMangaSeriesUseCase @Inject constructor(
    private val externalSearch: ExternalSearchRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(query: String): SeriesSearchResult {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return SeriesSearchResult.QueryTooShort
        if (authRepository.currentUserId() == null) return SeriesSearchResult.NotConnected
        return externalSearch.searchMangaSeries(trimmed).fold(
            onSuccess = { candidates ->
                if (candidates.isEmpty()) SeriesSearchResult.NoMatch
                else SeriesSearchResult.Matches(candidates)
            },
            onFailure = { cause ->
                val failure = cause as? ExternalSearchException
                SeriesSearchResult.Failed(
                    failure = failure?.failure ?: ExternalSearchFailure.UNKNOWN,
                    provider = failure?.provider
                )
            }
        )
    }

    companion object {
        /** Même seuil que la recherche de disques : en deçà, le bruit l'emporte sur le signal. */
        const val MIN_QUERY_LENGTH: Int = SearchExternalByTextUseCase.MIN_QUERY_LENGTH
    }
}

/**
 * Issue d'un [SearchMangaSeriesUseCase]. Le `403` reste possible malgré MangaDex sans jeton : si
 * MangaDex est en panne, le serveur replie sur MAL, qui en exige un — d'où [Failed] avec
 * `SOURCE_TOKEN_REQUIRED`, à router vers la connexion de source comme pour les disques.
 */
sealed interface SeriesSearchResult {

    /** Requête trop courte : aucun appel tenté. */
    data object QueryTooShort : SeriesSearchResult

    /** Aucune session : pas d'appel réseau tenté. */
    data object NotConnected : SeriesSearchResult

    /** Recherche effectuée, réellement aucune série. */
    data object NoMatch : SeriesSearchResult

    /** Au moins un candidat, dans l'ordre du serveur. */
    data class Matches(val candidates: List<SeriesCandidate>) : SeriesSearchResult

    /** Échec traduit : jeton manquant (repli MAL), réseau, quota. */
    data class Failed(
        val failure: ExternalSearchFailure,
        val provider: String? = null
    ) : SeriesSearchResult
}
