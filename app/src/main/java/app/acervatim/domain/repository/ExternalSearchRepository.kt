package app.acervatim.domain.repository

import app.acervatim.domain.model.ExternalMatch

/**
 * Recherche de métadonnées dans les bases externes, médiée par le backend (`GET /v1/search`, §8).
 * L'app n'appelle jamais Discogs/Google Books/AniList directement : tout transite par le serveur.
 *
 * Renvoie un [Result] : l'appelant doit distinguer « aucun résultat » (succès, liste vide) d'une
 * erreur (réseau, hors-ligne, quota, non configuré) pour préserver le repli offline-first.
 */
interface ExternalSearchRepository {

    /**
     * @param barcode EAN/UPC (vinyle) ou ISBN (manga) — passé tel quel en `q`, le backend gère le
     *   matching par code.
     * @param type code de collection aligné sur le contrat (`"vinyl"`, `"manga"`, …) — cf.
     *   `CollectionType.typeCode`.
     */
    suspend fun searchByBarcode(barcode: String, type: String): Result<List<ExternalMatch>>
}
