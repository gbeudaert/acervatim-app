package app.acervatim.domain.model

/**
 * Ordre et mise en forme des listes de collection — **partagés** par toutes les sources.
 *
 * Extraits de `RecordRepositoryImpl`, où ils étaient privés, parce qu'une collection reçue en
 * partage n'a pas de repository Room et doit pourtant s'afficher exactement pareil. Tant qu'ils y
 * vivaient, l'ordre était une propriété de la base plutôt que de la liste.
 *
 * Depuis le sprint AD2, le tri est **choisissable** ([RecordSort]) et descend en SQL pour la base
 * locale (`RecordQueries`). Les comparateurs ci-dessous restent la référence pour tout ce qui
 * n'a pas de SQL sous la main — une collection partagée, un test — et **doivent** donner le même
 * ordre que la requête : deux vues du même tri qui divergeraient seraient un bug invisible.
 */

val RECORD_BY_PRIMARY_ARTIST: Comparator<Record> = compareBy(
    { it.primaryArtist?.name?.lowercase().orEmpty() },
    { it.title.lowercase() }
)

val ENTRY_BY_DISPLAYED_ARTIST: Comparator<RecordArtistEntry> = compareBy(
    { it.displayedArtist.name.lowercase() },
    { it.record.title.lowercase() }
)

val RECORD_BY_VOLUME: Comparator<Record> = compareBy(
    { it.volumeNumber ?: Int.MAX_VALUE },
    { it.title.lowercase() }
)

/**
 * Comparateur correspondant à un [RecordSort]. Jumeau Kotlin de l'`ORDER BY` de `RecordQueries`.
 *
 * Deux règles valent pour **tous** les champs :
 * - les valeurs absentes vont en **fin de liste dans les deux sens**. Un disque jamais écouté n'a
 *   rien à faire en tête d'un tri par dernière écoute — ni au sommet, ni après inversion ;
 * - l'ordre est **total** : titre puis `id` départagent, sinon deux items de même clé
 *   changeraient de place d'un affichage à l'autre.
 */
fun recordComparator(sort: RecordSort): Comparator<Record> = when (sort.field) {
    RecordSortField.ARTIST -> orderedBy(sort.direction) { it.primaryArtist?.name?.lowercase() }
    RecordSortField.TITLE -> orderedBy(sort.direction) { it.title.lowercase() }
    RecordSortField.YEAR -> orderedBy(sort.direction) { it.year }
    RecordSortField.LAST_PLAYED -> orderedBy(sort.direction) { it.lastPlayedAt }
    RecordSortField.ADDED -> orderedBy(sort.direction) { it.createdAt }
    RecordSortField.RATING -> orderedBy(sort.direction) { it.rating }
}

/** Même tri, appliqué à une ligne disque × artiste (cf. [toEntriesFlattenedByArtist]). */
fun entryComparator(sort: RecordSort): Comparator<RecordArtistEntry> =
    if (sort.field == RecordSortField.ARTIST) {
        // La ligne est portée par l'artiste *affiché*, pas par l'artiste principal : en mode
        // doublons, un album à trois artistes doit atterrir sous chacun des trois.
        entriesOrderedBy(sort.direction) { it.displayedArtist.name.lowercase() }
    } else {
        val byRecord = recordComparator(sort)
        Comparator<RecordArtistEntry> { a, b -> byRecord.compare(a.record, b.record) }
            .thenBy { it.displayedArtist.name.lowercase() }
    }

private fun <T : Comparable<T>> orderedBy(
    direction: SortDirection,
    key: (Record) -> T?
): Comparator<Record> =
    nullsLastThenDirected(direction, key)
        .thenBy { it.title.lowercase() }
        .thenBy { it.id }

private fun <T : Comparable<T>> entriesOrderedBy(
    direction: SortDirection,
    key: (RecordArtistEntry) -> T?
): Comparator<RecordArtistEntry> =
    nullsLastThenDirected(direction, key)
        .thenBy { it.record.title.lowercase() }
        .thenBy { it.record.id }

/**
 * Absents d'abord écartés en fin de liste (indépendamment du sens), puis comparaison des
 * présents dans le sens demandé. Inverser l'ensemble ferait remonter les absents en tête.
 */
private fun <E, T : Comparable<T>> nullsLastThenDirected(
    direction: SortDirection,
    key: (E) -> T?
): Comparator<E> {
    val presentFirst = Comparator<E> { a, b ->
        val missingA = if (key(a) == null) 1 else 0
        val missingB = if (key(b) == null) 1 else 0
        missingA - missingB
    }
    val byValue = Comparator<E> { a, b ->
        val left = key(a)
        val right = key(b)
        if (left == null || right == null) 0 else left.compareTo(right)
    }
    val directed = if (direction == SortDirection.ASCENDING) byValue else byValue.reversed()
    return presentFirst.then(directed)
}

/**
 * Une ligne par disque, portée par son artiste principal.
 *
 * L'ordre d'entrée est **conservé** : il vient de l'`ORDER BY` de la requête (base locale) ou du
 * comparateur appliqué en amont (collection partagée). Retrier ici écraserait le tri choisi.
 *
 * Un disque sans artiste est écarté : la vue liste groupe par artiste, une entrée sans artiste
 * n'aurait pas de section où aller.
 */
fun List<Record>.toEntriesByPrimaryArtist(): List<RecordArtistEntry> =
    mapNotNull { record ->
        val primary = record.primaryArtist ?: return@mapNotNull null
        RecordArtistEntry(
            record = record,
            displayedArtist = primary,
            featuringArtists = record.featuringArtists
        )
    }

/**
 * Une ligne par **couple** disque × artiste : un album à trois artistes apparaît sous chacun des
 * trois. C'est le mode « doublons par artiste » du réglage correspondant.
 *
 * Seul cas où l'ordre est refait en mémoire, et seulement sur un tri par artiste : éclater un
 * disque en trois lignes qui doivent se ranger à trois endroits différents est précisément ce
 * qu'un `ORDER BY` sur des disques ne peut pas exprimer. Sur les autres tris, l'ordre des
 * disques suffit et les doublons restent groupés.
 */
fun List<Record>.toEntriesFlattenedByArtist(sort: RecordSort): List<RecordArtistEntry> =
    flatMap { record ->
        record.artists.map { artist ->
            RecordArtistEntry(
                record = record,
                displayedArtist = artist,
                featuringArtists = emptyList()
            )
        }
    }.let { entries ->
        if (sort.field == RecordSortField.ARTIST) entries.sortedWith(entryComparator(sort))
        else entries
    }
