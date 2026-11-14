package app.acervatim.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SeriesCatchUpRulesTest {

    private val now: Instant = Instant.parse("2026-11-02T10:00:00Z")

    private fun series(
        publisherFr: String? = "Pika",
        status: String? = null,
        type: CollectionType = CollectionType.MANGA,
        title: String = "Frieren"
    ) = Series(
        id = "s1",
        collectionType = type,
        title = title,
        publicationStatus = status,
        publisherFr = publisherFr,
        createdAt = now,
        updatedAt = now
    )

    private fun tome(volume: Int, isbn: String? = null, cover: String? = null) =
        EditionTome(editionVolume = volume, isbn = isbn, titleFr = "Frieren. $volume", coverUrl = cover)

    private fun record(volume: Int?, isbn: String? = null) = Record(
        id = "r$volume-$isbn",
        collectionType = CollectionType.MANGA,
        seriesId = "s1",
        volumeNumber = volume,
        artists = emptyList(),
        title = "t",
        isbn = isbn,
        createdAt = now,
        updatedAt = now
    )

    // ---- Éligibilité --------------------------------------------------------------------------

    @Test
    fun `une serie manga en cours avec editeur est suivie`() {
        assertTrue(series().isCatchUpTrackable())
        assertTrue(series(status = "En cours").isCatchUpTrackable())
    }

    @Test
    fun `sans editeur, terminee, ou hors manga, la serie n'est pas suivie`() {
        assertFalse(series(publisherFr = null).isCatchUpTrackable())
        assertFalse(series(publisherFr = "  ").isCatchUpTrackable())
        assertFalse(series(status = "Terminée").isCatchUpTrackable())
        assertFalse(series(status = "finished").isCatchUpTrackable())
        assertFalse(series(type = CollectionType.VINYL).isCatchUpTrackable())
        assertFalse(series(title = " ").isCatchUpTrackable())
    }

    // ---- Échéance et since --------------------------------------------------------------------

    @Test
    fun `premiere verification - partir de la creation, due apres 7 jours`() {
        val created = LocalDate.of(2026, 10, 20)
        val empty = SeriesCatchUpState()
        assertFalse(isCatchUpDue(empty, created, LocalDate.of(2026, 10, 26)))
        assertTrue(isCatchUpDue(empty, created, LocalDate.of(2026, 10, 27)))
        assertEquals(LocalDate.of(2026, 10, 13), catchUpSince(empty, created))
    }

    @Test
    fun `verifications suivantes - partir de la derniere verification moins la marge`() {
        val created = LocalDate.of(2026, 1, 1)
        val state = SeriesCatchUpState(lastCheckedOn = LocalDate.of(2026, 10, 30))
        assertFalse(isCatchUpDue(state, created, LocalDate.of(2026, 11, 5)))
        assertTrue(isCatchUpDue(state, created, LocalDate.of(2026, 11, 6)))
        assertEquals(LocalDate.of(2026, 10, 23), catchUpSince(state, created))
    }

    // ---- Dédoublonnage ------------------------------------------------------------------------

    @Test
    fun `un tome present par isbn OU par numero est ecarte`() {
        val volumes = listOf(record(13, "979-10-327-0000-1"), record(14, null))
        val found = listOf(
            tome(13, "9791032700001"), // même ISBN (normalisé), même n°
            tome(14, "9791032700002"), // n° déjà saisi à la main sans ISBN
            tome(99, "9791032700001"), // n° inconnu mais ISBN déjà présent
            tome(15, "9791032700003")  // vraiment nouveau
        )
        assertEquals(listOf(15), found.absentFrom(volumes).map { it.editionVolume })
    }

    @Test
    fun `la fusion garde un tome par isbn puis par numero, en preferant celui qui a une jaquette`() {
        val pending = listOf(tome(15, "9791032700003"))
        val found = listOf(
            tome(15, "9791032700003", cover = "https://c/15.jpg"),
            tome(16, "9791032700004"),
            tome(16, "9791032700099") // réimpression : autre ISBN, même tome
        )
        val merged = mergeCatchUpTomes(pending, found)
        assertEquals(listOf(15, 16), merged.map { it.editionVolume })
        assertEquals("https://c/15.jpg", merged.first().coverUrl)
    }
}
