package app.acervatim.domain.model

/**
 * Ordre et mise en forme des listes de collection — **partagés** par toutes les sources.
 *
 * Extraits de `RecordRepositoryImpl`, où ils étaient privés, parce qu'une collection reçue en
 * partage n'a pas de repository Room et doit pourtant s'afficher exactement pareil. Tant qu'ils y
 * vivaient, l'ordre était une propriété de la base plutôt que de la liste.
 *
 * C'est aussi ici qu'un futur choix de tri (par titre, par année, par date d'ajout…) doit atterrir :
 * posé à cet endroit, il vaut pour ses propres collections comme pour celles qu'on lui partage, sans
 * avoir à être écrit deux fois.
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
 * Une ligne par disque, portée par son artiste principal.
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
    }.sortedWith(ENTRY_BY_DISPLAYED_ARTIST)

/**
 * Une ligne par **couple** disque × artiste : un album à trois artistes apparaît sous chacun des
 * trois. C'est le mode « doublons par artiste » du réglage correspondant.
 */
fun List<Record>.toEntriesFlattenedByArtist(): List<RecordArtistEntry> =
    flatMap { record ->
        record.artists.map { artist ->
            RecordArtistEntry(
                record = record,
                displayedArtist = artist,
                featuringArtists = emptyList()
            )
        }
    }.sortedWith(ENTRY_BY_DISPLAYED_ARTIST)
