package app.acervatim.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.recordComparator

/**
 * Le prédicat en mémoire doit dire **exactement** ce que dit le `WHERE` de
 * `RecordDao.observeFilteredJoinRows`.
 *
 * Deux implémentations d'un même filtre, c'est une dérive qui attend son heure : ces cas fixent la
 * sémantique du SQL — insensibilité aux accents comprise (v8) — pour qu'une même recherche rende la
 * même chose sur sa collection et sur celle qu'on lui partage.
 */
class RecordFilterMatchingTest {

    private val now: Instant = Instant.parse("2026-08-23T10:00:00Z")

    private fun record(
        title: String = "Kind of Blue",
        artists: List<String> = listOf("Miles Davis"),
        type: CollectionType = CollectionType.VINYL,
        status: ItemStatus = ItemStatus.OWNED,
        genre: String? = null,
        format: AcervatimFormat? = null,
        lastPlayedAt: Instant? = null
    ) = Record(
        id = "r1",
        collectionType = type,
        artists = artists.map { Artist(id = "a-$it", name = it) },
        title = title,
        genre = genre,
        format = format,
        status = status,
        lastPlayedAt = lastPlayedAt,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    @Test
    fun `filtre vide - tout passe`() {
        assertTrue(RecordFilter().matches(record(), now))
    }

    @Test
    fun `recherche sur le titre, insensible a la casse comme LIKE en SQLite`() {
        assertTrue(RecordFilter(query = "kind").matches(record(), now))
        assertTrue(RecordFilter(query = "KIND").matches(record(), now))
        assertTrue(RecordFilter(query = "of bl").matches(record(), now))
    }

    @Test
    fun `recherche sur le nom d'un artiste quelconque, pas seulement le principal`() {
        val r = record(artists = listOf("Miles Davis", "John Coltrane"))
        assertTrue(RecordFilter(query = "coltrane").matches(r, now))
    }

    @Test
    fun `recherche sans correspondance`() {
        assertFalse(RecordFilter(query = "radiohead").matches(record(), now))
    }

    @Test
    fun `les espaces autour de la requete sont ignores`() {
        assertTrue(RecordFilter(query = "  kind  ").matches(record(), now))
    }

    /**
     * Depuis la v8, le SQL compare des colonnes **normalisées** (`normalized_title`,
     * `normalized_name`) à une saisie normalisée : insensible aux accents. Le prédicat en mémoire
     * suit, des deux côtés à la fois — une recherche qui trouverait sur une collection partagée et
     * pas sur la sienne serait pire qu'une recherche imparfaite mais cohérente.
     */
    @Test
    fun `insensible aux accents - comme le SQL depuis la v8`() {
        val r = record(title = "Éclat", artists = listOf("Björk"))
        assertTrue(RecordFilter(query = "eclat").matches(r, now))
        assertTrue(RecordFilter(query = "éclat").matches(r, now))
        assertTrue(RecordFilter(query = "ÉCLAT").matches(r, now))
        assertTrue(RecordFilter(query = "bjork").matches(r, now))
    }

    @Test
    fun `filtre de statut`() {
        val owned = record(status = ItemStatus.OWNED)
        val wish = record(status = ItemStatus.WISHLIST)
        val f = RecordFilter(status = StatusFilter.WISHLIST_ONLY)
        assertFalse(f.matches(owned, now))
        assertTrue(f.matches(wish, now))
        // ALL ne filtre rien.
        assertTrue(RecordFilter(status = StatusFilter.ALL).matches(owned, now))
    }

    @Test
    fun `filtres d'egalite - type, genre, format`() {
        val r = record(type = CollectionType.VINYL, genre = "Jazz", format = AcervatimFormat.LP_33)
        assertTrue(RecordFilter(collectionType = CollectionType.VINYL).matches(r, now))
        assertFalse(RecordFilter(collectionType = CollectionType.MANGA).matches(r, now))
        assertTrue(RecordFilter(genre = "Jazz").matches(r, now))
        assertFalse(RecordFilter(genre = "Rock").matches(r, now))
        assertTrue(RecordFilter(format = AcervatimFormat.LP_33).matches(r, now))
        assertFalse(RecordFilter(format = AcervatimFormat.SINGLE_45).matches(r, now))
    }

    @Test
    fun `un champ nul ne peut pas satisfaire un filtre sur ce champ`() {
        assertFalse(RecordFilter(genre = "Jazz").matches(record(genre = null), now))
    }

    @Test
    fun `pas ecoute depuis six mois - jamais ecoute compte comme non ecoute`() {
        val f = RecordFilter(notPlayedSinceSixMonths = true)
        assertTrue("lastPlayedAt null doit passer", f.matches(record(lastPlayedAt = null), now))
        assertTrue(f.matches(record(lastPlayedAt = now.minusSeconds(200 * 86_400)), now))
        assertFalse(f.matches(record(lastPlayedAt = now.minusSeconds(10 * 86_400)), now))
    }

    @Test
    fun `les criteres se combinent en ET`() {
        val r = record(title = "Kind of Blue", genre = "Jazz", status = ItemStatus.OWNED)
        val f = RecordFilter(query = "kind", genre = "Jazz", status = StatusFilter.OWNED_ONLY)
        assertTrue(f.matches(r, now))
        assertFalse(f.copy(genre = "Rock").matches(r, now))
    }

    @Test
    fun `l'ordre des entrees est celui de la liste locale - artiste puis titre`() {
        val records = listOf(
            record(title = "OK Computer", artists = listOf("Radiohead")),
            record(title = "Kind of Blue", artists = listOf("Miles Davis")),
            record(title = "Bitches Brew", artists = listOf("miles davis"))
        )
        val ordered = records
            .sortedWith(recordComparator(RecordSort.DEFAULT))
            .toEntriesByPrimaryArtist()
            .map { it.record.title }
        assertEquals(listOf("Bitches Brew", "Kind of Blue", "OK Computer"), ordered)
    }

    @Test
    fun `mode doublons - une ligne par artiste du disque`() {
        val r = record(artists = listOf("Daft Punk", "Pharrell Williams"))
        assertEquals(2, listOf(r).toEntriesFlattenedByArtist(RecordSort.DEFAULT).size)
    }

    @Test
    fun `un disque sans artiste n'a pas de section ou aller`() {
        assertTrue(listOf(record(artists = emptyList())).toEntriesByPrimaryArtist().isEmpty())
    }
}
