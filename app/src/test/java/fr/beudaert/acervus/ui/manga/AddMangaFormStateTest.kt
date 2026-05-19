package fr.beudaert.acervus.ui.manga

import fr.beudaert.acervus.domain.model.Artist
import fr.beudaert.acervus.domain.model.CollectionType
import fr.beudaert.acervus.domain.model.ItemStatus
import fr.beudaert.acervus.domain.model.MangaDemographic
import fr.beudaert.acervus.domain.model.MangaExtras
import fr.beudaert.acervus.domain.model.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Vérifie le mapping form ⇄ Record pour les tomes manga. Les champs manga-spécifiques sont
 * stockés dans `extras` et doivent survivre à un aller-retour fromRecord → toInput.
 */
class AddMangaFormStateTest {

    private val now: Instant = Instant.parse("2026-05-19T08:00:00Z")

    @Test
    fun `toInput emits manga extras and series binding`() {
        val state = AddMangaFormState(
            recordId = "r1",
            seriesId = "berserk",
            volumeNumber = "5",
            title = "Tome 5",
            originalAuthor = "Kentarō Miura",
            publisherFr = "Glénat",
            isbn = "9782723423052",
            demographic = MangaDemographic.SEINEN,
            pageCount = "240",
            rating = 4,
            status = ItemStatus.OWNED
        )

        val input = state.toInput(coverPath = "/cover/r1.jpg")

        assertEquals(CollectionType.MANGA, input.collectionType)
        assertEquals("berserk", input.seriesId)
        assertEquals(5, input.volumeNumber)
        assertEquals("Tome 5", input.title)
        assertEquals("9782723423052", input.barcode)
        assertEquals(4, input.rating)
        assertEquals(ItemStatus.OWNED, input.status)
        assertEquals("/cover/r1.jpg", input.coverPath)
        assertEquals(listOf("Kentarō Miura"), input.artists)

        assertEquals("9782723423052", input.extras[MangaExtras.KEY_ISBN])
        assertEquals("Glénat", input.extras[MangaExtras.KEY_PUBLISHER_FR])
        assertEquals("Kentarō Miura", input.extras[MangaExtras.KEY_ORIGINAL_AUTHOR])
        assertEquals("SEINEN", input.extras[MangaExtras.KEY_DEMOGRAPHIC])
        assertEquals("240", input.extras[MangaExtras.KEY_PAGE_COUNT])
    }

    @Test
    fun `toInput injects 'Inconnu' artist when originalAuthor is blank`() {
        // Le use case AddRecordUseCase requiert au moins un artiste non vide ; pour un manga
        // sans auteur saisi on stocke un placeholder explicite (l'auteur reste dans la série).
        val state = AddMangaFormState(
            recordId = "r1",
            seriesId = "berserk",
            title = "Tome 1",
            originalAuthor = "   "
        )

        val input = state.toInput(coverPath = null)
        assertEquals(listOf("Inconnu"), input.artists)
        assertEquals(null, input.extras[MangaExtras.KEY_ORIGINAL_AUTHOR])
    }

    @Test
    fun `fromRecord populates state from manga record`() {
        val record = Record(
            id = "r1",
            collectionType = CollectionType.MANGA,
            seriesId = "berserk",
            volumeNumber = 5,
            artists = listOf(Artist("a", "Kentarō Miura")),
            title = "Tome 5",
            status = ItemStatus.WISHLIST,
            rating = 5,
            coverPath = "/cover/r1.jpg",
            barcode = null,
            extras = mapOf(
                MangaExtras.KEY_ISBN to "9782723423052",
                MangaExtras.KEY_PUBLISHER_FR to "Glénat",
                MangaExtras.KEY_ORIGINAL_AUTHOR to "Kentarō Miura",
                MangaExtras.KEY_DEMOGRAPHIC to "SEINEN",
                MangaExtras.KEY_PAGE_COUNT to "240"
            ),
            createdAt = now,
            updatedAt = now
        )

        val state = AddMangaFormState().fromRecord(record)

        assertEquals("r1", state.recordId)
        assertEquals(true, state.isEditing)
        assertEquals("berserk", state.seriesId)
        assertEquals("5", state.volumeNumber)
        assertEquals("Tome 5", state.title)
        assertEquals("Kentarō Miura", state.originalAuthor)
        assertEquals("Glénat", state.publisherFr)
        assertEquals("9782723423052", state.isbn)
        assertEquals(MangaDemographic.SEINEN, state.demographic)
        assertEquals("240", state.pageCount)
        assertEquals(5, state.rating)
        assertEquals(ItemStatus.WISHLIST, state.status)
        assertEquals("/cover/r1.jpg", state.persistedCoverPath)
    }

    @Test
    fun `fromRecord falls back to record barcode when ISBN extra absent`() {
        val record = Record(
            id = "r1",
            collectionType = CollectionType.MANGA,
            seriesId = "berserk",
            artists = listOf(Artist("a", "Author")),
            title = "Tome 1",
            barcode = "9784088770734",
            status = ItemStatus.OWNED,
            createdAt = now,
            updatedAt = now
        )

        val state = AddMangaFormState().fromRecord(record)

        // Aucune entrée ISBN dans les extras → fallback sur le barcode lu en base.
        assertEquals("9784088770734", state.isbn)
        assertNull(state.demographic)
    }

    @Test
    fun `form is invalid without a series even when title is set`() {
        val state = AddMangaFormState(title = "Some title", seriesId = null)
        assertEquals(false, state.isValid)
    }
}
