package app.acervatim.domain.usecase

import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import app.acervatim.domain.model.PressingGroup
import app.acervatim.domain.model.groupPressings
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.ExternalSearchRepository
import javax.inject.Inject

/**
 * Cherche un disque dans les bases externes à partir d'un texte libre (titre, artiste), pour les
 * disques sans code-barres que le scan ne peut pas atteindre.
 *
 * Court-circuite l'appel réseau sur deux conditions, dans cet ordre : requête trop courte (une
 * requête d'un caractère consomme le quota Discogs pour rien) puis absence de session (la recherche
 * externe exige un JWT). Les résultats sortent **regroupés par album** : c'est la sélection du bon
 * pressage qui est le problème ici, pas la recherche.
 */
class SearchExternalByTextUseCase @Inject constructor(
    private val externalSearch: ExternalSearchRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(query: String, type: String): ExternalSearchResult {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return ExternalSearchResult.QueryTooShort
        if (authRepository.currentUserId() == null) return ExternalSearchResult.NotConnected
        return externalSearch.searchByText(trimmed, type).fold(
            onSuccess = { matches ->
                val groups = matches.groupPressings()
                if (groups.isEmpty()) ExternalSearchResult.NoMatch
                else ExternalSearchResult.Matches(groups)
            },
            onFailure = { cause ->
                val failure = cause as? ExternalSearchException
                ExternalSearchResult.Failed(
                    failure = failure?.failure ?: ExternalSearchFailure.UNKNOWN,
                    provider = failure?.provider
                )
            }
        )
    }

    companion object {
        /** En deçà, le bruit dépasse le signal et l'appel gaspille le quota de la source. */
        const val MIN_QUERY_LENGTH: Int = 3
    }
}

/**
 * Issue d'un [SearchExternalByTextUseCase]. Les cinq cas sont distincts parce qu'ils appellent cinq
 * messages distincts à l'écran : confondre [NoMatch] et [Failed] ferait croire à l'utilisateur que
 * son disque n'est pas référencé alors que c'est l'accès qui manque.
 */
sealed interface ExternalSearchResult {

    /** Requête trop courte : aucun appel tenté, l'écran invite simplement à préciser. */
    data object QueryTooShort : ExternalSearchResult

    /** Aucune session : pas d'appel réseau tenté. */
    data object NotConnected : ExternalSearchResult

    /** Recherche effectuée, réellement aucun résultat. */
    data object NoMatch : ExternalSearchResult

    /** Au moins un album ; les groupes suivent l'ordre de pertinence du serveur. */
    data class Matches(val groups: List<PressingGroup>) : ExternalSearchResult

    /** Échec traduit : jeton manquant, réseau, quota. */
    data class Failed(
        val failure: ExternalSearchFailure,
        val provider: String? = null
    ) : ExternalSearchResult
}
