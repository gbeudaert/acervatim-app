package app.acervatim.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Connexion des comptes de sources externes (Discogs, MyAnimeList, …), médiée par le backend
 * (`/v1/oauth/…`, §10). L'app ne voit **jamais** le jeton : elle déclenche le flow d'autorisation
 * (URL ouverte dans le navigateur), constate l'état connecté et peut révoquer.
 *
 * Offline-first : un backend muet / hors-ligne ne retire aucune fonctionnalité — [connectedProviders]
 * reste vide et l'enrichissement retombe sur le comportement dégradé côté serveur.
 */
interface OAuthRepository {

    /** Codes providers actuellement connectés (cf. [app.acervatim.domain.model.Source.providerCode]). */
    val connectedProviders: Flow<Set<String>>

    /** Recharge l'état depuis le backend (`GET /v1/oauth`). Erreur ⇒ état inchangé, pas d'exception. */
    suspend fun refresh()

    /**
     * Démarre la connexion d'un provider (`GET /v1/oauth/:provider/start`) et renvoie l'`authorizeUrl`
     * à ouvrir dans le navigateur. Le backend gère le callback et le stockage chiffré du jeton ;
     * l'app rappelle [refresh] à son retour au premier plan.
     */
    suspend fun startConnection(providerCode: String): Result<String>

    /** Révoque un provider (`DELETE /v1/oauth/:provider`). */
    suspend fun disconnect(providerCode: String): Result<Unit>
}
