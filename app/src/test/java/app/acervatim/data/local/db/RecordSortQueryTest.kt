package app.acervatim.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort
import app.acervatim.domain.model.RecordSortField
import app.acervatim.domain.model.SortDirection
import app.acervatim.domain.model.StatusFilter
import app.acervatim.domain.model.recordComparator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Le tri descendu en SQL, confronté au comparateur.
 *
 * L'assertion centrale n'est pas « la requête trie » mais « la requête trie **comme** le
 * comparateur » : les deux ordonnent les mêmes collections dans l'app (base locale d'un côté,
 * collection partagée de l'autre) et une divergence entre eux ne se verrait nulle part avant
 * qu'un utilisateur compare deux écrans.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecordSortQueryTest {

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
    fun `chaque champ et chaque sens - la requete donne l'ordre du comparateur`() = runTest {
        seedSampleCollection()

        RecordSortField.entries.forEach { field ->
            SortDirection.entries.forEach { direction ->
                val sort = RecordSort(field, direction)
                val fromSql = records(sort).map { it.id }
                val fromComparator = records(sort)
                    .sortedWith(recordComparator(sort))
                    .map { it.id }

                assertEquals("$field / $direction", fromComparator, fromSql)
            }
        }
    }

    @Test
    fun `jamais ecoutes - en fin de liste dans les deux sens`() = runTest {
        seedSampleCollection()

        SortDirection.entries.forEach { direction ->
            val ordered = records(RecordSort(RecordSortField.LAST_PLAYED, direction)).map { it.id }
            assertEquals(
                "un disque jamais écouté ne peut pas ouvrir un tri par dernière écoute",
                listOf("never-played"),
                ordered.takeLast(1)
            )
        }
    }

    @Test
    fun `ordre total - deux disques de meme annee gardent leur place`() = runTest {
        insertRecord("same-b", title = "Meme titre", artists = listOf("Zed"), year = 1970)
        insertRecord("same-a", title = "Meme titre", artists = listOf("Abe"), year = 1970)

        val sort = RecordSort(RecordSortField.YEAR, SortDirection.ASCENDING)
        val first = records(sort).map { it.id }
        val second = records(sort).map { it.id }

        assertEquals(listOf("same-a", "same-b"), first)
        assertEquals(first, second)
    }

    @Test
    fun `artiste principal - un disque a plusieurs artistes se range sur le premier`() = runTest {
        // "Zed" est en tête des artistes de ce disque : trier sur la ligne jointe le rangerait
        // sous "Abe", qui n'en est que le second artiste.
        insertRecord("multi", title = "Collab", artists = listOf("Zed", "Abe"), year = 2000)
        insertRecord("solo", title = "Solo", artists = listOf("Mel"), year = 2000)

        val ordered = records(RecordSort(RecordSortField.ARTIST, SortDirection.ASCENDING))
            .map { it.id }

        assertEquals(listOf("solo", "multi"), ordered)
    }

    @Test
    fun `les filtres continuent de s'appliquer sous un tri`() = runTest {
        insertRecord("owned", title = "Garde", artists = listOf("Abe"), year = 1990)
        insertRecord(
            "wishlist",
            title = "Voulu",
            artists = listOf("Bea"),
            year = 1980,
            status = ItemStatus.WISHLIST
        )

        val ordered = records(
            sort = RecordSort(RecordSortField.YEAR, SortDirection.ASCENDING),
            filter = RecordFilter(status = StatusFilter.OWNED_ONLY)
        ).map { it.id }

        assertEquals(listOf("owned"), ordered)
    }

    /** Collection d'essai : chaque champ triable y a des valeurs distinctes, et un trou. */
    private suspend fun seedSampleCollection() {
        insertRecord(
            "oldest",
            title = "Ancien",
            artists = listOf("Abe"),
            year = 1959,
            rating = 5,
            lastPlayedAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdAt = Instant.parse("2020-01-01T00:00:00Z")
        )
        insertRecord(
            "newest",
            title = "Recent",
            artists = listOf("mel"),
            year = 2021,
            rating = 2,
            lastPlayedAt = Instant.parse("2026-06-01T00:00:00Z"),
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        insertRecord(
            "middle",
            title = "Milieu",
            artists = listOf("Zed"),
            year = 1990,
            rating = 4,
            lastPlayedAt = Instant.parse("2026-03-01T00:00:00Z"),
            createdAt = Instant.parse("2023-01-01T00:00:00Z")
        )
        insertRecord(
            "never-played",
            title = "Jamais",
            artists = listOf("Bea"),
            year = null,
            rating = null,
            lastPlayedAt = null,
            createdAt = Instant.parse("2024-01-01T00:00:00Z")
        )
    }

    /** Première émission du flux, regroupée en disques — l'ordre des lignes est conservé. */
    private suspend fun records(
        sort: RecordSort,
        filter: RecordFilter = RecordFilter()
    ): List<Record> = recordDao
        .observeFilteredJoinRows(
            RecordQueries.filteredJoinRows(filter = filter, sort = sort, notPlayedSince = null)
        )
        .first()
        .groupToRecords()

    private suspend fun insertRecord(
        id: String,
        title: String,
        artists: List<String>,
        year: Int? = null,
        rating: Int? = null,
        lastPlayedAt: Instant? = null,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        status: ItemStatus = ItemStatus.OWNED
    ) {
        recordDao.upsertRecord(
            RecordEntity(
                id = id,
                collectionType = CollectionType.VINYL,
                seriesId = null,
                volumeNumber = null,
                title = title,
                genre = null,
                year = year,
                label = null,
                format = AcervatimFormat.LP_33,
                recordingSpeed = null,
                country = null,
                purchasePrice = null,
                rating = rating,
                coverPath = null,
                coverRemoteUrl = null,
                barcode = null,
                isbn = null,
                discogsId = null,
                status = status,
                lastPlayedAt = lastPlayedAt,
                extras = emptyMap(),
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )
        val ids = artists.map { name ->
            val artistId = "artist-${name.lowercase().replace(' ', '_')}"
            artistDao.upsert(
                ArtistEntity(id = artistId, name = name, normalizedName = name.lowercase())
            )
            artistId
        }
        recordDao.replaceCrossRefs(id, ids)
    }
}
