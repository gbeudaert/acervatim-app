package app.acervatim.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Agrégats d'écoute d'une série (`MAX(lastPlayedAt)`, `SUM(playCount)`) : ce sont eux qui
 * alimentent les rubriques de la galerie côté séries, sans jamais charger les tomes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeriesDaoPlaybackTest {

    private lateinit var db: AcervatimDatabase
    private lateinit var seriesDao: SeriesDao
    private lateinit var recordDao: RecordDao

    private val now: Instant = Instant.parse("2026-08-24T10:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AcervatimDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        seriesDao = db.seriesDao()
        recordDao = db.recordDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `une serie cumule les compteurs de ses tomes et garde l'ecoute la plus recente`() = runTest {
        seriesDao.upsert(seriesEntity("s1", "Berserk"))
        recordDao.upsertRecord(volume("v1", "s1", playCount = 3, lastPlayedAt = now.minusSeconds(600)))
        recordDao.upsertRecord(volume("v2", "s1", playCount = 4, lastPlayedAt = now))

        seriesDao.observeWithCountsByCollectionType(CollectionType.MANGA).test {
            val row = awaitItem().single()
            assertEquals(7, row.playCount)
            assertEquals(now, row.lastPlayedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `une serie jamais ecoutee compte zero, sans date`() = runTest {
        seriesDao.upsert(seriesEntity("s1", "Vagabond"))
        recordDao.upsertRecord(volume("v1", "s1"))

        seriesDao.observeWithCountsByCollectionType(CollectionType.MANGA).test {
            val row = awaitItem().single()
            assertEquals(0, row.playCount)
            assertNull(row.lastPlayedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun seriesEntity(id: String, title: String): SeriesEntity = SeriesEntity(
        id = id,
        collectionType = CollectionType.MANGA,
        title = title,
        originalAuthor = null,
        normalizedTitle = title.lowercase(),
        normalizedAuthor = "",
        publicationStatus = null,
        totalVolumes = null,
        synopsis = null,
        coverPath = null,
        createdAt = now,
        updatedAt = now
    )

    private fun volume(
        id: String,
        seriesId: String,
        playCount: Int = 0,
        lastPlayedAt: Instant? = null
    ): RecordEntity = RecordEntity(
        id = id,
        collectionType = CollectionType.MANGA,
        seriesId = seriesId,
        volumeNumber = 1,
        title = "Tome $id",
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
        barcode = null,
        isbn = null,
        discogsId = null,
        status = ItemStatus.OWNED,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        extras = emptyMap(),
        createdAt = now,
        updatedAt = now
    )
}
