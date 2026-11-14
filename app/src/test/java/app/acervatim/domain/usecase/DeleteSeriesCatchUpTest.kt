package app.acervatim.domain.usecase

import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesCatchUpStore
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesCatchUpState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Supprimer une série oublie aussi son état de rattrapage (AD3), indexé par série. */
class DeleteSeriesCatchUpTest {

    @Test
    fun `la suppression d'une serie efface son etat de rattrapage, pas celui des autres`() = runTest {
        val records = FakeRecordRepository()
        val series = FakeSeriesRepository(records)
        val store = FakeSeriesCatchUpStore()
        series.upsert(
            Series(id = "s1", collectionType = CollectionType.MANGA, title = "Frieren", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        )
        store.states["s1"] = SeriesCatchUpState()
        store.states["s2"] = SeriesCatchUpState()

        DeleteSeriesUseCase(series, records, store)("s1", cascade = false)

        assertEquals(listOf("s1"), store.cleared)
        assertNull(store.states["s1"])
        assertEquals(setOf("s2"), store.states.keys)
    }
}
