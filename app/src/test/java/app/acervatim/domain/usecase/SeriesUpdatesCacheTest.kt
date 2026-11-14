package app.acervatim.domain.usecase

import app.acervatim.domain.FakeExternalSearchRepository
import app.acervatim.domain.model.SeriesUpdate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Cache de session de `series-updates` : un téléchargement par TTL et par compte, pas plus. */
class SeriesUpdatesCacheTest {

    /** Horloge qu'on avance à la main, pour franchir le TTL. */
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-11-02T10:00:00Z"))
    private val search = FakeExternalSearchRepository().apply {
        seriesUpdatesResult = Result.success(listOf(SeriesUpdate("node-1", "Frieren", null, "Ki-oon", emptyList())))
    }
    private val cache = SeriesUpdatesCache(search, clock)

    @Test
    fun `dans le TTL la reponse est servie depuis la memoire`() = runTest {
        cache.get("u1")
        clock.now = clock.now.plus(Duration.ofMinutes(9))
        cache.get("u1")

        assertEquals(1, search.seriesUpdatesCallCount)
    }

    @Test
    fun `au-dela du TTL on retelecharge`() = runTest {
        cache.get("u1")
        clock.now = clock.now.plus(SeriesUpdatesCache.TTL)
        cache.get("u1")

        assertEquals(2, search.seriesUpdatesCallCount)
    }

    @Test
    fun `un autre compte ne relit jamais la reponse du precedent`() = runTest {
        cache.get("u1")
        cache.get("u2")

        assertEquals(2, search.seriesUpdatesCallCount)
    }

    @Test
    fun `invalidate force un nouveau telechargement`() = runTest {
        cache.get("u1")
        cache.invalidate()
        cache.get("u1")

        assertEquals(2, search.seriesUpdatesCallCount)
    }

    @Test
    fun `une erreur n'est pas mise en cache`() = runTest {
        search.seriesUpdatesResult = Result.failure(IOException("offline"))
        assertNull(cache.get("u1"))

        search.seriesUpdatesResult = Result.success(emptyList())
        cache.get("u1")

        assertEquals(2, search.seriesUpdatesCallCount)
    }
}
