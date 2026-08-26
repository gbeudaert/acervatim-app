package app.acervatim.domain.repository

import app.acervatim.domain.model.EditionMapping
import app.acervatim.domain.model.ExternalMatch
import app.acervatim.domain.model.ImportJob

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

    /**
     * Recherche en texte libre (titre, artiste) — le chemin de secours des disques **sans
     * code-barres** (pressages indépendants, autoproductions), introuvables par le scan.
     *
     * Symétrique de [searchByBarcode] : l'implémentation envoie `q` **XOR** `barcode`, jamais les
     * deux — le backend rejette une requête qui porte les deux.
     *
     * Contrairement au code-barres, un texte renvoie couramment vingt à trente pressages du même
     * album : les résultats portent de quoi les départager (année, label, pays, format, numéro de
     * catalogue) et se regroupent par `masterId` — cf. `groupPressings()`.
     *
     * L'échec est rendu en `failure(ExternalSearchException)` : la cause transport est traduite en
     * [app.acervatim.domain.model.ExternalSearchFailure] dès la couche `data`, pour que l'appelant
     * distingue « aucun résultat » (succès, liste vide) d'un refus de jeton, qui demande une action.
     *
     * @param query texte saisi, non vide.
     * @param type code de collection aligné sur le contrat (`"vinyl"`, …).
     */
    suspend fun searchByText(query: String, type: String): Result<List<ExternalMatch>>

    /**
     * Énumère une édition manga complète via le backend (`GET /v1/search/edition-mapping`), pour
     * importer « toute la série d'un coup ». Appel optionnel et explicite (quota) : réservé au bouton
     * « Importer tous les tomes connus ».
     *
     * @param titleFr titre FR de la série (source : [ExternalMatch.seriesTitleFr]).
     * @param edition mention d'édition (205), null pour l'édition standard.
     */
    suspend fun editionMapping(titleFr: String, edition: String?): Result<EditionMapping>

    /**
     * Résout la jaquette d'un tome par ISBN via le backend (`GET /v1/search/cover`) : résout côté
     * serveur (réseau) **et** alimente le cache serveur. Appel **explicite** réservé à l'import (un
     * par tome créé sans jaquette connue) — jamais en arrière-plan (politique réseau §6).
     *
     * [seriesTitleFr] + [volumeNumber] (et [edition] si spéciale) arment le **repli par titre** côté
     * serveur quand la notice ISBN n'a pas d'image — à fournir dès qu'ils sont connus, sans quoi les
     * ISBN papier FR sans jaquette repartent en cache négatif serveur pour plusieurs jours.
     *
     * @return `success(url)` si une jaquette est trouvée, `success(null)` si aucune, `failure` sur
     *   erreur réseau/quota — l'appelant traite l'échec en best-effort (import non bloqué).
     */
    suspend fun resolveCover(
        isbn: String,
        seriesTitleFr: String? = null,
        volumeNumber: Int? = null,
        edition: String? = null
    ): Result<String?>

    /**
     * Lance (ou réutilise) l'import **asynchrone** d'une édition complète côté backend
     * (`POST /v1/search/import-jobs`) : énumération BnF + jaquettes en tâche de fond, hors du cycle
     * requête-réponse du scan. Idempotent — deux demandes identiques partagent le même job (`jobId`
     * déterministe). L'appelant poll ensuite [importJobStatus] puis, au `DONE`, re-tape
     * [editionMapping] (cache serveur réchauffé). Un échec (réseau, Redis down → `503`) est rendu en
     * `failure` : l'appelant dégrade alors vers l'import « tome scanné seul ».
     */
    suspend fun startImportJob(titleFr: String, edition: String?): Result<ImportJob>

    /**
     * Statut + avancement d'un job d'import (`GET /v1/search/import-jobs/:id`). Renvoie
     * `success(null)` si le job est **inconnu** (`404` — expiré/purgé), distingué d'une erreur réseau
     * (`failure`) pour que le poll puisse conclure proprement.
     */
    suspend fun importJobStatus(jobId: String): Result<ImportJob?>
}
