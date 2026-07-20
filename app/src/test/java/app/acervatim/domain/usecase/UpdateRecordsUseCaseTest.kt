package app.acervatim.domain.usecase

import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.MangaExtras
import app.acervatim.domain.model.Record
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateRecordsUseCaseTest {

    private val fixedInstant: Instant = Instant.parse("2026-07-20T09:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val records = FakeRecordRepository()
    private val useCase = UpdateRecordsUseCase(records, UpdateRecordUseCase(records, clock))

    @Test
    fun `set status flips all selected entries and leaves other fields intact`() = runTest {
        records.upsert(rec("1", genre = "Rock", year = 1971))
        records.upsert(rec("2", genre = "Jazz", year = 1959))
        records.upsert(rec("3", genre = "Funk", year = 1976))

        val result = useCase(
            listOf("1", "2", "3"),
            BulkEditDelta(status = FieldEdit.Set(ItemStatus.OWNED))
        )

        assertEquals(listOf("1", "2", "3"), result.updated)
        assertFalse(result.hasFailures)
        assertTrue(records.listAll().all { it.status == ItemStatus.OWNED })
        // Champs Keep : valeurs hétérogènes d'origine préservées par entrée.
        assertEquals("Rock", records.byId("1").genre)
        assertEquals(1959, records.byId("2").year)
        assertEquals("Funk", records.byId("3").genre)
    }

    @Test
    fun `keep preserves heterogeneous values`() = runTest {
        records.upsert(rec("1", genre = "Rock"))
        records.upsert(rec("2", genre = "Jazz"))

        useCase(listOf("1", "2"), BulkEditDelta(status = FieldEdit.Set(ItemStatus.OWNED)))

        assertEquals("Rock", records.byId("1").genre)
        assertEquals("Jazz", records.byId("2").genre)
    }

    @Test
    fun `clear on a nullable field nulls it out`() = runTest {
        records.upsert(rec("1", rating = 5))
        records.upsert(rec("2", rating = 3))

        val result = useCase(listOf("1", "2"), BulkEditDelta(rating = FieldEdit.Clear))

        assertEquals(2, result.updatedCount)
        assertNull(records.byId("1").rating)
        assertNull(records.byId("2").rating)
    }

    @Test
    fun `set applies the same value to every entry`() = runTest {
        records.upsert(rec("1", genre = "Rock"))
        records.upsert(rec("2", genre = null))

        useCase(listOf("1", "2"), BulkEditDelta(genre = FieldEdit.Set("Compilation")))

        assertEquals("Compilation", records.byId("1").genre)
        assertEquals("Compilation", records.byId("2").genre)
    }

    @Test
    fun `a failing entry lands in failures while the others are updated`() = runTest {
        records.upsert(rec("1", genre = "Rock"))
        records.upsert(rec("bad", title = "")) // titre vide → validation échoue pour cette entrée
        records.upsert(rec("3", genre = "Funk"))

        val result = useCase(
            listOf("1", "bad", "3"),
            BulkEditDelta(status = FieldEdit.Set(ItemStatus.OWNED))
        )

        assertEquals(setOf("1", "3"), result.updated.toSet())
        assertEquals(listOf("bad"), result.failed.map { it.id })
        assertEquals(ItemStatus.OWNED, records.byId("1").status)
        // L'entrée en échec n'est pas modifiée (reste Wishlist).
        assertEquals(ItemStatus.WISHLIST, records.byId("bad").status)
    }

    @Test
    fun `artists set replaces the whole list per entry`() = runTest {
        records.upsert(rec("1", artists = listOf("Old One", "Old Two")))

        useCase(listOf("1"), BulkEditDelta(artists = FieldEdit.Set(listOf("New Artist"))))

        assertEquals(listOf("New Artist"), records.byId("1").artists.map { it.name })
    }

    @Test
    fun `title is never modified by a bulk edit`() = runTest {
        records.upsert(rec("1", title = "Original Title"))

        useCase(
            listOf("1"),
            BulkEditDelta(status = FieldEdit.Set(ItemStatus.OWNED), genre = FieldEdit.Set("X"))
        )

        assertEquals("Original Title", records.byId("1").title)
    }

    @Test
    fun `manga extras are set and cleared`() = runTest {
        records.upsert(
            rec(
                "1",
                collectionType = CollectionType.MANGA,
                extras = mapOf(MangaExtras.KEY_DEMOGRAPHIC to "Shonen")
            )
        )

        useCase(
            listOf("1"),
            BulkEditDelta(
                publisherFr = FieldEdit.Set("Ki-oon"),
                demographic = FieldEdit.Clear
            )
        )

        val extras = records.byId("1").extras
        assertEquals("Ki-oon", extras[MangaExtras.KEY_PUBLISHER_FR])
        assertFalse(extras.containsKey(MangaExtras.KEY_DEMOGRAPHIC))
    }

    @Test
    fun `empty delta is a no-op`() = runTest {
        records.upsert(rec("1"))

        val result = useCase(listOf("1"), BulkEditDelta())

        assertTrue(result.updated.isEmpty())
        assertFalse(result.hasFailures)
    }

    private suspend fun FakeRecordRepository.byId(id: String): Record =
        listAll().first { it.id == id }

    private fun rec(
        id: String,
        title: String = "Title $id",
        collectionType: CollectionType = CollectionType.VINYL,
        status: ItemStatus = ItemStatus.WISHLIST,
        genre: String? = null,
        year: Int? = null,
        rating: Int? = null,
        artists: List<String> = listOf("Artist"),
        extras: Map<String, String> = emptyMap()
    ): Record = Record(
        id = id,
        collectionType = collectionType,
        artists = artists.map { Artist(id = "", name = it) },
        title = title,
        genre = genre,
        year = year,
        rating = rating,
        status = status,
        extras = extras,
        createdAt = fixedInstant,
        updatedAt = fixedInstant
    )
}
