package app.acervatim.domain.model

import java.time.LocalDate

/**
 * État du **rattrapage** d'une série manga en cours (AD3) : quand l'app a vérifié pour la dernière
 * fois les nouveaux tomes de son édition, et ceux qu'elle a trouvés sans que l'utilisateur les ait
 * encore ajoutés.
 *
 * Les tomes trouvés sont **gardés** ([pending]) plutôt que redemandés : la fenêtre `since` avance à
 * chaque vérification réussie, donc un tome écarté d'un « Plus tard » ne reviendrait jamais d'une
 * requête suivante. Le garder ici est ce qui permet de le reproposer sans le perdre.
 */
data class SeriesCatchUpState(
    /**
     * Jour de la dernière vérification **aboutie et complète**. `null` tant qu'aucune n'a réussi :
     * la date de création de la série tient alors lieu de point de départ.
     */
    val lastCheckedOn: LocalDate? = null,
    /** Tomes trouvés, pas encore ajoutés, triés par n° — filtrés à l'affichage contre la série. */
    val pending: List<EditionTome> = emptyList(),
    /**
     * Jour du dernier « Plus tard ». La bannière reste masquée jusqu'à la vérification réussie
     * suivante, qui remet ce champ à `null`.
     */
    val dismissedOn: LocalDate? = null
)

/**
 * Intervalle minimal entre deux vérifications. Un tome n'apparaît à la BnF que des semaines après
 * sa sortie (étude du 2026-10-27, § 2 : +22 à +87 jours mesurés) : vérifier plus souvent ne
 * trouverait rien de plus et coûterait une requête SRU à chaque ouverture.
 */
const val CATCH_UP_INTERVAL_DAYS: Long = 7

/**
 * Marge retranchée à la dernière vérification pour fixer `since` : une notice créée la veille d'une
 * vérification peut n'être indexée par le SRU qu'après. Les doublons que la marge réintroduit sont
 * écartés par ISBN et par n° de tome.
 */
const val CATCH_UP_SINCE_MARGIN_DAYS: Long = 7

/**
 * La série relève-t-elle du rattrapage ? Il faut de quoi rejouer `edition-mapping` (titre de série
 * et éditeur — sans éditeur, le serveur retiendrait l'éditeur dominant, peut-être pas celui de
 * l'utilisateur) et une parution qui n'est pas close.
 */
fun Series.isCatchUpTrackable(): Boolean =
    collectionType == CollectionType.MANGA &&
        title.isNotBlank() &&
        !publisherFr.isNullOrBlank() &&
        !isPublicationFinished

/** Point de départ de la fenêtre : la dernière vérification réussie, sinon la création de la série. */
fun catchUpReference(state: SeriesCatchUpState, seriesCreatedOn: LocalDate): LocalDate =
    state.lastCheckedOn ?: seriesCreatedOn

/** Vrai quand la dernière vérification (ou la création) date d'au moins [CATCH_UP_INTERVAL_DAYS]. */
fun isCatchUpDue(state: SeriesCatchUpState, seriesCreatedOn: LocalDate, today: LocalDate): Boolean =
    !today.isBefore(catchUpReference(state, seriesCreatedOn).plusDays(CATCH_UP_INTERVAL_DAYS))

/** `since` de la requête : point de départ moins la marge d'indexation. */
fun catchUpSince(state: SeriesCatchUpState, seriesCreatedOn: LocalDate): LocalDate =
    catchUpReference(state, seriesCreatedOn).minusDays(CATCH_UP_SINCE_MARGIN_DAYS)

/**
 * Tomes **absents** de la série : ni leur ISBN (normalisé) ni leur n° de tome n'y figurent. Les deux
 * critères à la fois — un tome saisi à la main n'a souvent pas d'ISBN, un tome scanné peut avoir un
 * n° décalé ; l'un ou l'autre suffit à dire que l'utilisateur l'a déjà.
 */
fun List<EditionTome>.absentFrom(volumes: List<Record>): List<EditionTome> {
    val isbns = volumes.mapNotNull { normalizeIsbn(it.isbn) }.toSet()
    val numbers = volumes.mapNotNull { it.volumeNumber }.toSet()
    return filter { tome ->
        val isbn = normalizeIsbn(tome.isbn)
        (isbn == null || isbn !in isbns) && tome.editionVolume !in numbers
    }
}

/**
 * Fusionne des tomes trouvés à ceux déjà en attente : un seul tome par ISBN, puis un seul par n°
 * (une réimpression porte un autre ISBN pour le même tome). En cas de doublon, on garde la version
 * qui a une jaquette — c'est elle qui s'affichera une fois le tome ajouté.
 */
fun mergeCatchUpTomes(pending: List<EditionTome>, found: List<EditionTome>): List<EditionTome> =
    (pending + found)
        .sortedBy { if (it.coverUrl.isNullOrBlank()) 1 else 0 }
        .distinctBy { normalizeIsbn(it.isbn) ?: "vol:${it.editionVolume}" }
        .distinctBy { it.editionVolume }
        .sortedBy { it.editionVolume }
