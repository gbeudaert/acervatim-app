package app.acervatim.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.normalizeEditionKey
import app.acervatim.domain.model.normalizePublisherKey
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
 * Clé naturelle v8 d'une série : (type, titre, auteur, **éditeur, édition**). Deux éditions d'une
 * même œuvre coexistent (arbitrage du 2026-08-26) ; deux graphies d'un même éditeur, non.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeriesNaturalKeyTest {

    private lateinit var db: AcervatimDatabase
    private lateinit var seriesDao: SeriesDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AcervatimDatabase::class.java
        ).allowMainThreadQueries().build()
        seriesDao = db.seriesDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `standard Pika, 2-en-1 France Loisirs et colossale sont trois series`() = runTest {
        seriesDao.upsert(snk("s-pika", publisher = "Pika édition", edition = null).toEntity(""))
        seriesDao.upsert(snk("s-fl", publisher = "Éditions France loisirs", edition = null).toEntity(""))
        seriesDao.upsert(snk("s-colo", publisher = "Pika", edition = "Éd. colossale").toEntity(""))

        assertEquals(3, seriesDao.listAll().size)
    }

    @Test
    fun `deux graphies du meme editeur designent la meme serie`() = runTest {
        seriesDao.upsert(snk("s-pika", publisher = "Pika éd.", edition = null).toEntity(""))

        // Le libellé de `search/editions` (« Pika ») retrouve la série créée au scan (« Pika éd. »).
        val found = seriesDao.findByNaturalKey(
            collectionType = CollectionType.MANGA,
            normalizedTitle = normalizeSeriesKey("L'attaque des titans"),
            normalizedAuthor = normalizeSeriesKey("Hajime Isayama"),
            normalizedPublisher = normalizePublisherKey("Pika"),
            normalizedEdition = normalizeEditionKey(null)
        )

        assertEquals("s-pika", found?.id)
    }

    @Test
    fun `l'edition survit a l'aller-retour entite - domaine`() = runTest {
        seriesDao.upsert(
            snk("s-colo", publisher = "Pika", edition = "Éd. colossale", mangaId = "md-1").toEntity("")
        )

        val restored = seriesDao.getEntityById("s-colo")!!.toDomain()

        assertEquals("Éd. colossale", restored.edition)
        assertEquals("Pika", restored.publisherFr)
        assertEquals("md-1", restored.mangaId)
    }

    private fun snk(id: String, publisher: String?, edition: String?, mangaId: String? = null) = Series(
        id = id,
        collectionType = CollectionType.MANGA,
        title = "L'attaque des titans",
        originalAuthor = "Hajime Isayama",
        edition = edition,
        publisherFr = publisher,
        mangaId = mangaId,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
