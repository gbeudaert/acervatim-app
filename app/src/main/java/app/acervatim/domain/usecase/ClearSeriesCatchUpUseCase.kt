package app.acervatim.domain.usecase

import app.acervatim.domain.repository.SeriesCatchUpStore
import javax.inject.Inject

/**
 * Oublie tout l'état de rattrapage de l'appareil (attentes, dates, bannières écartées) et les
 * nouveautés serveur en mémoire. Appelé à la **déconnexion** et à la suppression du compte : cet état
 * a été calculé pour un utilisateur, il ne doit ni survivre à sa session ni fuiter vers le compte
 * suivant. Le changement de compte sans déconnexion explicite (session expirée puis reconnexion sous
 * un autre compte) est rattrapé par [SeriesCatchUpUseCase], qui vérifie le propriétaire de l'état.
 */
class ClearSeriesCatchUpUseCase @Inject constructor(
    private val store: SeriesCatchUpStore,
    private val updatesCache: SeriesUpdatesCache
) {
    suspend operator fun invoke() {
        updatesCache.invalidate()
        store.clearAll(owner = null)
    }
}
