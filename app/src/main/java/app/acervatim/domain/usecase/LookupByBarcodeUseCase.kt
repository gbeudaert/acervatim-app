package app.acervatim.domain.usecase

import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.repository.AuthRepository
import app.acervatim.domain.repository.ExternalSearchRepository
import javax.inject.Inject

/**
 * Cherche des métadonnées externes pour un code-barres scanné, en vue de pré-remplir le formulaire
 * d'ajout. Court-circuite l'appel réseau si aucune session n'est active (offline-first : la recherche
 * externe exige un JWT). Le ViewModel décide quoi faire de chaque issue.
 */
class LookupByBarcodeUseCase @Inject constructor(
    private val externalSearch: ExternalSearchRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(barcode: String, type: String): LookupResult {
        if (barcode.isBlank()) return LookupResult.NoMatch
        if (authRepository.currentUserId() == null) return LookupResult.NotConnected
        return externalSearch.searchByBarcode(barcode, type).fold(
            onSuccess = { matches ->
                if (matches.isEmpty()) LookupResult.NoMatch else LookupResult.Matches(matches)
            },
            onFailure = { LookupResult.Error(it) }
        )
    }
}

/** Issue d'un [LookupByBarcodeUseCase]. Toute issue ≠ [Matches] retombe sur la saisie manuelle. */
sealed interface LookupResult {
    /** Aucune session : pas d'appel réseau tenté. */
    data object NotConnected : LookupResult

    /** Recherche effectuée mais aucun résultat exploitable. */
    data object NoMatch : LookupResult

    /** Au moins un résultat ; `matches` est trié par pertinence (1er = meilleur). */
    data class Matches(val matches: List<ExternalMatch>) : LookupResult

    /** Échec réseau / serveur / quota. `cause` est une `ApiException` ou une `IOException`. */
    data class Error(val cause: Throwable) : LookupResult
}
