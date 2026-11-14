package app.acervatim.domain.repository

import app.acervatim.domain.model.SeriesCatchUpState

/**
 * Persistance locale de l'état de rattrapage par série ([SeriesCatchUpState]).
 *
 * État **propre à l'appareil**, ni synchronisé ni sauvegardé : il ne dit que « quand ai-je regardé
 * pour la dernière fois » et « qu'ai-je trouvé ». Le perdre ne coûte qu'une vérification de plus.
 * Il est rattaché à l'utilisateur pour lequel il a été calculé ([owner]) : un autre compte repart
 * de zéro.
 */
interface SeriesCatchUpStore {

    /** État de la série, ou un état vide si elle n'a jamais été vérifiée. */
    suspend fun read(seriesId: String): SeriesCatchUpState

    suspend fun write(seriesId: String, state: SeriesCatchUpState)

    /** Oublie l'état d'une série (supprimée, ou plus suivie). */
    suspend fun clear(seriesId: String)

    /** Utilisateur pour lequel l'état a été calculé, `null` si aucun (jamais rattaché, ou vidé). */
    suspend fun owner(): String?

    /**
     * Oublie l'état de **toutes** les séries (déconnexion, changement de compte) et rattache le
     * stockage vide à [owner] (`null` = à personne).
     */
    suspend fun clearAll(owner: String?)
}
