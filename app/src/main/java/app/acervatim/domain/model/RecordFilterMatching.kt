package app.acervatim.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Le filtre de collection, appliqué **en mémoire**.
 *
 * Miroir volontairement littéral du `WHERE` de `RecordDao.observeFilteredJoinRows` : les collections
 * locales sont filtrées en SQL (elles peuvent être grosses, et Room fait ça mieux que nous), mais une
 * collection reçue en partage n'a pas de table — elle est lue en ligne et n'entre jamais dans Room.
 * Il fallait donc le même prédicat des deux côtés.
 *
 * **Deux implémentations d'un même filtre, c'est un risque de dérive assumé**, et borné de deux
 * façons : le prédicat tient en quelques lignes, et un test (`RecordFilterMatchingTest`) le confronte
 * au SQL sur un même corpus. Toute évolution du filtre doit passer par les deux — ou par aucun.
 *
 * Ce qui n'est PAS ici : le tri et le regroupement par lettre. Ils sont déjà en Kotlin
 * (`RecordRepositoryImpl` pour l'ordre, `CollectionUiState.from` pour les sections) et donc
 * naturellement communs aux deux chemins.
 */
fun RecordFilter.matches(record: Record, now: Instant): Boolean {
    // `LIKE '%q%'` sur le titre OU sur le nom d'un artiste quelconque. SQLite est insensible à la
    // casse sur l'ASCII mais **pas** aux accents : on reproduit ce comportement plutôt que de le
    // corriger ici, sans quoi une même recherche ne rendrait pas la même chose selon la collection.
    val q = query.trim()
    if (q.isNotEmpty()) {
        val inTitle = record.title.contains(q, ignoreCase = true)
        val inArtist = record.artists.any { it.name.contains(q, ignoreCase = true) }
        if (!inTitle && !inArtist) return false
    }

    collectionType?.let { if (record.collectionType != it) return false }
    status.toItemStatus()?.let { if (record.status != it) return false }
    genre?.let { if (record.genre != it) return false }
    format?.let { if (record.format != it) return false }

    if (notPlayedSinceSixMonths) {
        val cutoff = now.minus(NOT_PLAYED_SINCE_DAYS, ChronoUnit.DAYS)
        // `lastPlayedAt IS NULL OR lastPlayedAt < :cutoff` — jamais écouté compte comme « pas
        // écouté depuis », c'est la sémantique du SQL et celle qu'attend l'utilisateur.
        val lastPlayed = record.lastPlayedAt
        if (lastPlayed != null && !lastPlayed.isBefore(cutoff)) return false
    }

    return true
}

/** Même fenêtre que `RecordRepositoryImpl.SIX_MONTHS_DAYS`. */
private const val NOT_PLAYED_SINCE_DAYS: Long = 183L
