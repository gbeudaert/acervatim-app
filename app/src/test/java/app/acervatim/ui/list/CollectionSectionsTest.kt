package app.acervatim.ui.list

import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.RecordSortField
import app.acervatim.domain.model.SortDirection
import app.acervatim.domain.usecase.CollectionStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Découpe de la liste plate en sections.
 *
 * Ce qui est vérifié ici tient en une phrase : la liste **n'invente pas d'ordre**. Elle n'affiche
 * un en-tête que si l'ordre est vraiment alphabétique, la lettre suit le champ trié, et le
 * regroupement ne réordonne jamais ce qui lui arrive.
 */
class CollectionSectionsTest {

    private val now: Instant = Instant.parse("2026-08-24T12:00:00Z")
    private val stats = CollectionStats(ownedCount = 3, wishlistCount = 0)

    @Test
    fun `tri par artiste - un en-tete par lettre d'artiste`() {
        val state = flatState(
            entries = listOf(
                entry("r1", title = "Discovery", artist = "Daft Punk"),
                entry("r2", title = "Kind of Blue", artist = "Miles Davis"),
                entry("r3", title = "OK Computer", artist = "Radiohead")
            ),
            sort = RecordSort(RecordSortField.ARTIST)
        )

        assertEquals(listOf("D", "M", "R"), state.sections.map { it.letter })
    }

    @Test
    fun `tri par titre - la lettre vient du titre, pas de l'artiste`() {
        val state = flatState(
            entries = listOf(
                entry("r1", title = "Amnesiac", artist = "Radiohead"),
                entry("r2", title = "Blonde", artist = "Frank Ocean")
            ),
            sort = RecordSort(RecordSortField.TITLE)
        )

        assertEquals(listOf("A", "B"), state.sections.map { it.letter })
    }

    @Test
    fun `tri par annee - aucun en-tete, la liste reste d'un seul tenant`() {
        val entries = listOf(
            entry("r1", title = "Récent", artist = "Zed"),
            entry("r2", title = "Ancien", artist = "Abe")
        )

        val state = flatState(entries = entries, sort = RecordSort(RecordSortField.YEAR))

        assertEquals(1, state.sections.size)
        assertNull(
            "un en-tête « A » sur un tri par année annoncerait un classement qui n'existe pas",
            state.sections.single().letter
        )
        assertEquals(entries, state.sections.single().entries)
    }

    @Test
    fun `le regroupement ne reordonne pas - un tri descendant reste descendant`() {
        val state = flatState(
            entries = listOf(
                entry("r3", title = "OK Computer", artist = "Radiohead"),
                entry("r2", title = "Kind of Blue", artist = "Miles Davis"),
                entry("r1", title = "Discovery", artist = "Daft Punk")
            ),
            sort = RecordSort(RecordSortField.ARTIST, SortDirection.DESCENDING)
        )

        assertEquals(listOf("R", "M", "D"), state.sections.map { it.letter })
    }

    private fun flatState(
        entries: List<RecordArtistEntry>,
        sort: RecordSort
    ): CollectionUiState.FlatContent = CollectionUiState.from(
        filter = RecordFilter(collectionType = CollectionType.VINYL, listMode = ListMode.FLAT),
        entries = entries,
        series = emptyList(),
        records = emptyList(),
        stats = stats,
        displayMode = DisplayMode.LIST,
        sort = sort,
        now = now
    ) as CollectionUiState.FlatContent

    private fun entry(id: String, title: String, artist: String): RecordArtistEntry {
        val artistModel = Artist(id = "a-$artist", name = artist)
        return RecordArtistEntry(
            record = Record(
                id = id,
                collectionType = CollectionType.VINYL,
                artists = listOf(artistModel),
                title = title,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH
            ),
            displayedArtist = artistModel,
            featuringArtists = emptyList()
        )
    }
}
