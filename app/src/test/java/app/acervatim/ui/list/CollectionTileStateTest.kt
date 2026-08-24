package app.acervatim.ui.list

import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.DisplayMode
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordArtistEntry
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesWithCounts
import app.acervatim.domain.usecase.CollectionStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Rubriques de la galerie et tuiles de la grille — la logique de tri/découpage qui décide de ce
 * que l'utilisateur voit en premier. Testée sur [CollectionUiState.from], sans Compose ni Room.
 */
class CollectionTileStateTest {

    private val now: Instant = Instant.parse("2026-08-24T12:00:00Z")
    private val stats = CollectionStats(ownedCount = 3, wishlistCount = 0)

    @Test
    fun `la grille est une rubrique unique portant toute la collection`() {
        val state = tileState(
            records = listOf(record("1", "A"), record("2", "B")),
            displayMode = DisplayMode.GRID
        )

        assertEquals(listOf(TileSectionKind.ALL), state.sections.map { it.kind })
        assertEquals(listOf("1", "2"), state.sections.single().tiles.map { it.id })
    }

    @Test
    fun `une rubrique vide est absente plutot qu'affichee vide`() {
        // Collection neuve : aucune écoute, donc ni « derniers » ni « plus écoutés ».
        val state = tileState(
            records = listOf(record("1", "A"), record("2", "B")),
            displayMode = DisplayMode.GALLERY
        )

        assertTrue(TileSectionKind.RECENTLY_PLAYED !in state.sections.map { it.kind })
        assertTrue(TileSectionKind.MOST_PLAYED !in state.sections.map { it.kind })
        // Jamais écoutés : ils sont tous « oubliés », et la collection entière ferme la galerie.
        assertEquals(
            listOf(TileSectionKind.FORGOTTEN, TileSectionKind.ALL),
            state.sections.map { it.kind }
        )
    }

    @Test
    fun `les derniers ecoutes sont tries du plus recent au plus ancien`() {
        val state = tileState(
            records = listOf(
                record("vieux", "A", lastPlayedAt = now.minus(30, ChronoUnit.DAYS), playCount = 1),
                record("hier", "B", lastPlayedAt = now.minus(1, ChronoUnit.DAYS), playCount = 1),
                record("jamais", "C")
            ),
            displayMode = DisplayMode.GALLERY
        )

        assertEquals(listOf("hier", "vieux"), state.section(TileSectionKind.RECENTLY_PLAYED).map { it.id })
    }

    @Test
    fun `les plus ecoutes sont tries par compteur et excluent les jamais ecoutes`() {
        val state = tileState(
            records = listOf(
                record("deux", "A", lastPlayedAt = now, playCount = 2),
                record("dix", "B", lastPlayedAt = now, playCount = 10),
                record("zero", "C")
            ),
            displayMode = DisplayMode.GALLERY
        )

        assertEquals(listOf("dix", "deux"), state.section(TileSectionKind.MOST_PLAYED).map { it.id })
    }

    @Test
    fun `la tuile porte le compteur pour que la rubrique le justifie`() {
        val state = tileState(
            records = listOf(record("dix", "B", lastPlayedAt = now, playCount = 10)),
            displayMode = DisplayMode.GALLERY
        )

        assertEquals(10, state.section(TileSectionKind.MOST_PLAYED).single().playCount)
    }

    @Test
    fun `les oublies sont ceux jamais ecoutes ou plus depuis six mois`() {
        val state = tileState(
            records = listOf(
                record("recent", "A", lastPlayedAt = now.minus(10, ChronoUnit.DAYS), playCount = 1),
                record("ancien", "B", lastPlayedAt = now.minus(300, ChronoUnit.DAYS), playCount = 1),
                record("jamais", "C")
            ),
            displayMode = DisplayMode.GALLERY
        )

        assertEquals(
            setOf("ancien", "jamais"),
            state.section(TileSectionKind.FORGOTTEN).map { it.id }.toSet()
        )
    }

    @Test
    fun `une rubrique est une mise en avant bornee, pas un catalogue`() {
        val many = (1..30).map { i ->
            record("r$i", "A", lastPlayedAt = now.minus(i.toLong(), ChronoUnit.DAYS), playCount = i)
        }
        val state = tileState(records = many, displayMode = DisplayMode.GALLERY)

        assertEquals(
            CollectionUiState.GALLERY_SECTION_SIZE,
            state.section(TileSectionKind.RECENTLY_PLAYED).size
        )
        // La rubrique « toute la collection » n'est pas bornée : sinon la galerie cacherait le reste.
        assertEquals(30, state.section(TileSectionKind.ALL).size)
    }

    @Test
    fun `la galerie des series agrege les ecoutes de ses tomes`() {
        val state = CollectionUiState.from(
            filter = RecordFilter(collectionType = CollectionType.MANGA, listMode = ListMode.SERIAL),
            entries = emptyList(),
            series = listOf(
                seriesWithCounts("s1", "Berserk", lastPlayedAt = now, playCount = 7),
                seriesWithCounts("s2", "Vagabond")
            ),
            records = emptyList(),
            stats = stats,
            displayMode = DisplayMode.GALLERY,
            now = now
        ) as CollectionUiState.TileContent

        assertTrue(state.serial)
        assertEquals(listOf("s1"), state.section(TileSectionKind.MOST_PLAYED).map { it.id })
        assertEquals(7, state.section(TileSectionKind.MOST_PLAYED).single().playCount)
    }

    @Test
    fun `les series en liste ou en grille restent une liste de series`() {
        listOf(DisplayMode.LIST, DisplayMode.GRID).forEach { mode ->
            val state = CollectionUiState.from(
                filter = RecordFilter(collectionType = CollectionType.MANGA, listMode = ListMode.SERIAL),
                entries = emptyList(),
                series = listOf(seriesWithCounts("s1", "Berserk")),
                records = emptyList(),
                stats = stats,
                displayMode = mode,
                now = now
            )
            val serial = state as CollectionUiState.SerialContent
            assertEquals(mode, serial.displayMode)
        }
    }

    @Test
    fun `la liste plate reste groupee par lettre`() {
        val state = CollectionUiState.from(
            filter = RecordFilter(collectionType = CollectionType.VINYL),
            entries = listOf(entry(record("1", "Beatles"), "Beatles")),
            series = emptyList(),
            records = emptyList(),
            stats = stats,
            displayMode = DisplayMode.LIST,
            now = now
        )

        val flat = state as CollectionUiState.FlatContent
        assertEquals("B", flat.sections.single().letter)
    }

    @Test
    fun `une collection sans rien reste un etat vide, meme en galerie`() {
        val state = CollectionUiState.from(
            filter = RecordFilter(collectionType = CollectionType.VINYL),
            entries = emptyList(),
            series = emptyList(),
            records = emptyList(),
            stats = CollectionStats(ownedCount = 0, wishlistCount = 0),
            displayMode = DisplayMode.GALLERY,
            now = now
        )

        assertTrue(state is CollectionUiState.Empty)
    }

    // ---- Fixtures ------------------------------------------------------------------------------

    private fun tileState(
        records: List<Record>,
        displayMode: DisplayMode
    ): CollectionUiState.TileContent = CollectionUiState.from(
        filter = RecordFilter(collectionType = CollectionType.VINYL),
        entries = emptyList(),
        series = emptyList(),
        records = records,
        stats = stats,
        displayMode = displayMode,
        now = now
    ) as CollectionUiState.TileContent

    private fun CollectionUiState.TileContent.section(kind: TileSectionKind): List<CollectionTile> =
        sections.first { it.kind == kind }.tiles

    private fun record(
        id: String,
        artist: String,
        lastPlayedAt: Instant? = null,
        playCount: Int = 0
    ): Record = Record(
        id = id,
        artists = listOf(Artist(id = "a-$artist", name = artist)),
        title = "Titre $id",
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        createdAt = now,
        updatedAt = now
    )

    private fun entry(record: Record, artist: String): RecordArtistEntry = RecordArtistEntry(
        record = record,
        displayedArtist = Artist(id = "a-$artist", name = artist),
        featuringArtists = emptyList()
    )

    private fun seriesWithCounts(
        id: String,
        title: String,
        lastPlayedAt: Instant? = null,
        playCount: Int = 0
    ): SeriesWithCounts = SeriesWithCounts(
        series = Series(
            id = id,
            collectionType = CollectionType.MANGA,
            title = title,
            createdAt = now,
            updatedAt = now
        ),
        ownedVolumes = 1,
        wishlistVolumes = 0,
        ignoredVolumes = 0,
        totalVolumesInLibrary = 1,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount
    )
}
