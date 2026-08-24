package app.acervatim.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.sync.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordDaoTest {

    private lateinit var db: AcervatimDatabase
    private lateinit var recordDao: RecordDao
    private lateinit var artistDao: ArtistDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AcervatimDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        recordDao = db.recordDao()
        artistDao = db.artistDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeFilteredJoinRows emits empty list initially`() = runTest {
        filteredRows().test {
            assertEquals(emptyList<RecordWithArtistsRow>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `inserting a record with two artists exposes two join rows`() = runTest {
        val now = Instant.parse("2026-05-16T10:00:00Z")
        insertRecord(id = "rec-1", title = "Get Lucky", artists = listOf("Daft Punk", "Pharrell"), now = now)

        filteredRows().test {
            val rows = awaitItem()
            assertEquals(2, rows.size)
            val names = rows.sortedBy { it.position }.map { it.artistName }
            assertEquals(listOf("Daft Punk", "Pharrell"), names)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query matches title or any artist name`() = runTest {
        val now = Instant.parse("2026-05-16T10:00:00Z")
        insertRecord("r1", "Animals", listOf("Pink Floyd"), now)
        insertRecord("r2", "Random Access Memories", listOf("Daft Punk", "Nile Rodgers"), now)

        filteredRows(RecordFilter(query = "nile")).test {
            val rows = awaitItem()
            val recordIds = rows.map { it.record.id }.distinct()
            assertEquals(listOf("r2"), recordIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeByIdJoinRows returns artists ordered by position`() = runTest {
        val now = Instant.parse("2026-05-16T10:00:00Z")
        insertRecord("rec-1", "T", listOf("Z Artist", "A Artist", "M Artist"), now)

        recordDao.observeByIdJoinRows("rec-1").test {
            val rows = awaitItem()
            assertEquals(listOf("Z Artist", "A Artist", "M Artist"), rows.map { it.artistName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `findByBarcodeJoinRows returns matching record's artists`() = runTest {
        val now = Instant.parse("2026-05-16T10:00:00Z")
        insertRecord("rec-1", "X", listOf("A", "B"), now, barcode = "1234567890")

        val rows = recordDao.findByBarcodeJoinRows("1234567890")
        assertNotNull(rows.firstOrNull())
        assertEquals(2, rows.size)
        assertEquals("rec-1", rows.first().record.id)
        assertNull(recordDao.findByBarcodeJoinRows("0000000000").firstOrNull())
    }

    @Test
    fun `deleting a record cascades to its cross refs`() = runTest {
        val now = Instant.parse("2026-05-16T10:00:00Z")
        insertRecord("rec-1", "X", listOf("A"), now)

        recordDao.deleteById("rec-1")

        filteredRows().test {
            assertEquals(emptyList<RecordWithArtistsRow>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceCrossRefs swaps artists for a record`() = runTest {
        val now = Instant.parse("2026-05-16T10:00:00Z")
        val a1 = insertArtist("A")
        val a2 = insertArtist("B")
        recordDao.upsertRecord(recordEntity("rec-1", "T", now))
        recordDao.replaceCrossRefs("rec-1", listOf(a1, a2))

        recordDao.observeByIdJoinRows("rec-1").test {
            val rows = awaitItem()
            assertEquals(listOf("A", "B"), rows.map { it.artistName })
            cancelAndIgnoreRemainingEvents()
        }

        val a3 = insertArtist("C")
        recordDao.replaceCrossRefs("rec-1", listOf(a3))

        recordDao.observeByIdJoinRows("rec-1").test {
            val rows = awaitItem()
            assertEquals(listOf("C"), rows.map { it.artistName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markPlayed horodate et incremente dans la meme ecriture`() = runTest {
        val now = Instant.parse("2026-08-24T10:00:00Z")
        insertRecord("rec-1", "T", listOf("A"), now)

        recordDao.markPlayed("rec-1", playedAt = now, updatedAt = now)
        recordDao.markPlayed("rec-1", playedAt = now.plusSeconds(60), updatedAt = now)

        val entity = recordDao.getEntityById("rec-1")!!
        assertEquals(2, entity.playCount)
        assertEquals(now.plusSeconds(60), entity.lastPlayedAt)
        // Toute écriture partielle repasse la ligne en DIRTY pour la prochaine synchro.
        assertEquals(SyncState.DIRTY, entity.syncState)
    }

    @Test
    fun `resetPlayback efface la date et le compteur ensemble`() = runTest {
        val now = Instant.parse("2026-08-24T10:00:00Z")
        insertRecord("rec-1", "T", listOf("A"), now)
        recordDao.markPlayed("rec-1", playedAt = now, updatedAt = now)

        recordDao.resetPlayback("rec-1", updatedAt = now)

        val entity = recordDao.getEntityById("rec-1")!!
        assertEquals(0, entity.playCount)
        assertNull(entity.lastPlayedAt)
    }

    private suspend fun insertArtist(name: String): String {
        val id = "artist-${name.lowercase().replace(' ', '_')}"
        artistDao.upsert(ArtistEntity(id = id, name = name, normalizedName = name.lowercase()))
        return id
    }

    private suspend fun insertRecord(
        id: String,
        title: String,
        artists: List<String>,
        now: Instant,
        barcode: String? = null
    ) {
        recordDao.upsertRecord(recordEntity(id, title, now, barcode))
        val ids = artists.map { insertArtist(it) }
        recordDao.replaceCrossRefs(id, ids)
    }

    private fun recordEntity(
        id: String,
        title: String,
        now: Instant,
        barcode: String? = null
    ): RecordEntity = RecordEntity(
        id = id,
        collectionType = CollectionType.VINYL,
        seriesId = null,
        volumeNumber = null,
        title = title,
        genre = null,
        year = null,
        label = null,
        format = AcervatimFormat.LP_33,
        recordingSpeed = null,
        country = null,
        purchasePrice = null,
        rating = null,
        coverPath = null,
        coverRemoteUrl = null,
        barcode = barcode,
        isbn = null,
        discogsId = null,
        status = ItemStatus.OWNED,
        lastPlayedAt = null,
        extras = emptyMap(),
        createdAt = now,
        updatedAt = now
    )

    /**
     * La requête filtrée est désormais construite hors du DAO (l'`ORDER BY` dépend du tri) : le
     * test passe par le même constructeur que la production plutôt que par un SQL parallèle.
     */
    private fun filteredRows(
        filter: RecordFilter = RecordFilter(),
        sort: RecordSort = RecordSort.DEFAULT
    ) = recordDao.observeFilteredJoinRows(
        RecordQueries.filteredJoinRows(filter = filter, sort = sort, notPlayedSince = null)
    )
}
