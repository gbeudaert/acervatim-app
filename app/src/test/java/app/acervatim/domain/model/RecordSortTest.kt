package app.acervatim.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Comportement du tri, côté comparateur — la référence que la requête SQL doit reproduire.
 *
 * Deux propriétés valent pour tous les champs et sont vérifiées champ par champ : les valeurs
 * absentes restent en fin de liste **même une fois le sens inversé**, et l'ordre est total (aucun
 * couple ne peut permuter d'un affichage à l'autre).
 */
class RecordSortTest {

    @Test
    fun `tri par artiste - A vers Z puis Z vers A`() {
        val records = listOf(
            record("r1", title = "Discovery", artists = listOf("Daft Punk")),
            record("r2", title = "Kind of Blue", artists = listOf("Miles Davis")),
            record("r3", title = "OK Computer", artists = listOf("radiohead"))
        )

        assertEquals(
            listOf("r1", "r2", "r3"),
            records.ids(RecordSortField.ARTIST, SortDirection.ASCENDING)
        )
        assertEquals(
            listOf("r3", "r2", "r1"),
            records.ids(RecordSortField.ARTIST, SortDirection.DESCENDING)
        )
    }

    @Test
    fun `tri par titre - insensible a la casse`() {
        val records = listOf(
            record("r2", title = "Blonde"),
            record("r1", title = "amnesiac")
        )

        assertEquals(
            "une minuscule ne doit pas passer derrière toutes les majuscules",
            listOf("r1", "r2"),
            records.ids(RecordSortField.TITLE, SortDirection.ASCENDING)
        )
    }

    /**
     * Limite assumée, épinglée ici pour qu'elle soit connue plutôt que découverte : ni le
     * comparateur ni la requête n'appliquent de collation locale, donc un titre accentué se
     * range après « Z ». Les deux moteurs sont d'accord entre eux, c'est ce qui compte ; les
     * réconcilier avec l'alphabet français demanderait une collation dédiée des deux côtés.
     */
    @Test
    fun `tri par titre - un accent initial passe apres Z, des deux cotes`() {
        val records = listOf(
            record("r1", title = "amnesiac"),
            record("r2", title = "Zoo"),
            record("r3", title = "Ágætis byrjun")
        )

        assertEquals(
            listOf("r1", "r2", "r3"),
            records.ids(RecordSortField.TITLE, SortDirection.ASCENDING)
        )
    }

    @Test
    fun `tri par annee - les deux sens`() {
        val records = listOf(
            record("r1", year = 1997),
            record("r2", year = 1959),
            record("r3", year = 2001)
        )

        assertEquals(
            listOf("r2", "r1", "r3"),
            records.ids(RecordSortField.YEAR, SortDirection.ASCENDING)
        )
        assertEquals(
            listOf("r3", "r1", "r2"),
            records.ids(RecordSortField.YEAR, SortDirection.DESCENDING)
        )
    }

    @Test
    fun `tri par note - les deux sens`() {
        val records = listOf(
            record("r1", rating = 3),
            record("r2", rating = 5),
            record("r3", rating = 1)
        )

        assertEquals(
            listOf("r2", "r1", "r3"),
            records.ids(RecordSortField.RATING, SortDirection.DESCENDING)
        )
        assertEquals(
            listOf("r3", "r1", "r2"),
            records.ids(RecordSortField.RATING, SortDirection.ASCENDING)
        )
    }

    @Test
    fun `tri par derniere ecoute et par ajout - les deux sens`() {
        val records = listOf(
            record("r1", lastPlayedAt = DAY_1, createdAt = DAY_1),
            record("r2", lastPlayedAt = DAY_3, createdAt = DAY_3),
            record("r3", lastPlayedAt = DAY_2, createdAt = DAY_2)
        )

        assertEquals(
            listOf("r2", "r3", "r1"),
            records.ids(RecordSortField.LAST_PLAYED, SortDirection.DESCENDING)
        )
        assertEquals(
            listOf("r1", "r3", "r2"),
            records.ids(RecordSortField.ADDED, SortDirection.ASCENDING)
        )
    }

    @Test
    fun `les valeurs absentes restent en fin de liste dans les deux sens`() {
        val fields = mapOf(
            RecordSortField.YEAR to record("missing", year = null),
            RecordSortField.RATING to record("missing", rating = null),
            RecordSortField.LAST_PLAYED to record("missing", lastPlayedAt = null),
            RecordSortField.ARTIST to record("missing", artists = emptyList())
        )

        fields.forEach { (field, missing) ->
            val present = listOf(
                record("r1", year = 1990, rating = 2, lastPlayedAt = DAY_1),
                record("r2", year = 2010, rating = 5, lastPlayedAt = DAY_3)
            )
            val records = listOf(missing) + present

            SortDirection.entries.forEach { direction ->
                assertEquals(
                    "$field / $direction : l'absent doit fermer la marche",
                    "missing",
                    records.ids(field, direction).last()
                )
            }
        }
    }

    @Test
    fun `ordre total - deux disques de meme cle ne permutent pas`() {
        val records = listOf(
            record("r-b", title = "Meme titre", year = 1970),
            record("r-a", title = "Meme titre", year = 1970)
        )

        val once = records.ids(RecordSortField.YEAR, SortDirection.ASCENDING)
        val twice = records.reversed().ids(RecordSortField.YEAR, SortDirection.ASCENDING)

        assertEquals(listOf("r-a", "r-b"), once)
        assertEquals("le départage par id rend l'ordre indépendant de l'entrée", once, twice)
    }

    @Test
    fun `mode doublons - un disque a trois artistes se range sous chacun`() {
        val record = record("r1", artists = listOf("Daft Punk", "Pharrell Williams"))
        val others = listOf(record("r2", artists = listOf("Nile Rodgers")))

        val entries = (listOf(record) + others)
            .toEntriesFlattenedByArtist(RecordSort(RecordSortField.ARTIST))
            .map { it.displayedArtist.name }

        assertEquals(listOf("Daft Punk", "Nile Rodgers", "Pharrell Williams"), entries)
    }

    @Test
    fun `sens naturel - les recents et les mieux notes en tete, les noms de A a Z`() {
        assertEquals(SortDirection.ASCENDING, RecordSortField.ARTIST.naturalDirection)
        assertEquals(SortDirection.ASCENDING, RecordSortField.TITLE.naturalDirection)
        assertEquals(SortDirection.DESCENDING, RecordSortField.LAST_PLAYED.naturalDirection)
        assertEquals(SortDirection.DESCENDING, RecordSortField.RATING.naturalDirection)
        assertEquals(SortDirection.DESCENDING, RecordSortField.ADDED.naturalDirection)
    }

    @Test
    fun `en-tetes alphabetiques - seulement quand l'ordre est alphabetique`() {
        assertTrue(RecordSortField.ARTIST.hasLetterSections)
        assertTrue(RecordSortField.TITLE.hasLetterSections)
        assertFalse(RecordSortField.YEAR.hasLetterSections)
        assertFalse(RecordSortField.LAST_PLAYED.hasLetterSections)
        assertFalse(RecordSortField.ADDED.hasLetterSections)
        assertFalse(RecordSortField.RATING.hasLetterSections)
    }

    private fun List<Record>.ids(field: RecordSortField, direction: SortDirection): List<String> =
        sortedWith(recordComparator(RecordSort(field, direction))).map { it.id }

    private fun record(
        id: String,
        title: String = "Titre",
        artists: List<String> = listOf("Artiste"),
        year: Int? = null,
        rating: Int? = null,
        lastPlayedAt: Instant? = null,
        createdAt: Instant = Instant.EPOCH
    ) = Record(
        id = id,
        collectionType = CollectionType.VINYL,
        artists = artists.map { Artist(id = "a-$it", name = it) },
        title = title,
        year = year,
        rating = rating,
        lastPlayedAt = lastPlayedAt,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private companion object {
        val DAY_1: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val DAY_2: Instant = Instant.parse("2026-02-01T00:00:00Z")
        val DAY_3: Instant = Instant.parse("2026-03-01T00:00:00Z")
    }
}
