package app.acervatim.ui.scan

import app.acervatim.domain.model.ExternalMatch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passe-plat en mémoire pour transporter un résultat enrichi ([ExternalMatch]) vers le formulaire
 * d'ajout, sans le sérialiser dans la route de navigation (la jaquette/description seraient trop
 * volumineuses et fragiles en arg de nav). La navigation dépile l'écran d'origine, donc on ne peut
 * pas s'appuyer sur son `SavedStateHandle` : ce singleton survit.
 *
 * Deux écrans y déposent : le scan ([BarcodeScanViewModel]) et la recherche par titre
 * ([app.acervatim.ui.search.ExternalSearchViewModel]). Le nom garde sa dette d'origine — c'est le
 * même passe-plat, et le formulaire n'a pas à savoir par quel chemin le match est arrivé.
 *
 * Le match est associé à une **clé** que la route porte ensuite en argument : le code-barres pour le
 * scan, l'identité `source:sourceId` du pressage choisi pour la recherche par titre (qui, elle, n'a
 * pas toujours de code-barres — c'est sa raison d'être). Le formulaire ne consomme le match que si
 * la clé correspond, et le vide à la lecture (usage unique).
 */
@Singleton
class ScanPrefillHandoff @Inject constructor() {

    private var pendingKey: String? = null
    private var pendingMatch: ExternalMatch? = null

    /** Mémorise le match retenu pour [key]. Écrase tout match en attente. */
    fun put(key: String, match: ExternalMatch) {
        pendingKey = key
        pendingMatch = match
    }

    /** Récupère et efface le match si (et seulement si) il correspond à [key]. */
    fun take(key: String): ExternalMatch? {
        if (key.isBlank() || key != pendingKey) return null
        val match = pendingMatch
        pendingKey = null
        pendingMatch = null
        return match
    }
}
