package app.acervatim.domain.usecase

import app.acervatim.domain.model.SeriesUpdate
import app.acervatim.domain.repository.ExternalSearchRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache **mémoire, par session** des nouveautés serveur (`GET /v1/series-updates`).
 *
 * La réponse couvre **toutes** les séries synchronisées du compte : la retélécharger à chaque
 * ouverture d'une série (ce que faisait le rattrapage) coûtait un appel complet par écran pour n'en
 * lire qu'une ligne. Le job serveur ne tourne qu'une fois par semaine : une réponse de moins de
 * [TTL] est aussi fraîche qu'une neuve.
 *
 * Indexé par utilisateur : un autre compte ne relit jamais les nouveautés du précédent, même sans
 * [invalidate] explicite. Une erreur (réseau, `402` hors premium) n'est pas mise en cache — elle
 * n'empêche pas l'appel suivant.
 */
@Singleton
class SeriesUpdatesCache @Inject constructor(
    private val externalSearch: ExternalSearchRepository,
    private val clock: Clock
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: Entry? = null

    /**
     * Nouveautés du compte [userId], au plus un téléchargement par [TTL]. `null` si le serveur ne les
     * a pas fournies (erreur, hors premium) : l'appelant s'en tient au rattrapage local.
     */
    suspend fun get(userId: String): List<SeriesUpdate>? = mutex.withLock {
        val now = clock.instant()
        cached?.takeIf { it.userId == userId && Duration.between(it.fetchedAt, now) < TTL }
            ?.let { return it.updates }
        val updates = externalSearch.seriesUpdates().getOrNull() ?: return null
        cached = Entry(userId, now, updates)
        updates
    }

    /** Oublie la réponse en mémoire (déconnexion, changement de compte). */
    fun invalidate() {
        cached = null
    }

    private data class Entry(val userId: String, val fetchedAt: Instant, val updates: List<SeriesUpdate>)

    companion object {
        /** Fraîcheur tolérée : le job serveur est hebdomadaire, dix minutes ne font rien perdre. */
        val TTL: Duration = Duration.ofMinutes(10)
    }
}
