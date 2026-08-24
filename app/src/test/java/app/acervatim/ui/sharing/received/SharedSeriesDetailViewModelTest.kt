package app.acervatim.ui.sharing.received

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.sharing.SharedItemDetail
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharedCollectionContent
import app.acervatim.domain.repository.SharedCollectionRepository
import app.acervatim.domain.repository.SharedSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Détail d'une série partagée : chargement ciblé, et les trois façons dont un accès s'arrête.
 *
 * Le rendu lui-même n'est pas testé ici — c'est `SeriesDetailBody`, le composant partagé avec les
 * séries locales. C'est précisément l'intérêt du découpage : il n'y a rien à retester deux fois.
 */
class SharedSeriesDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = SharedSeriesDetailViewModel(
        repository,
        SavedStateHandle(
            mapOf(
                SharedSeriesDetailViewModel.COLLECTION_ID_ARG to "col-1",
                SharedSeriesDetailViewModel.SERIES_ID_ARG to "n1"
            )
        )
    )

    @Test
    fun `charge la serie et ses tomes, sans recharger la collection`() = runTest {
        repository.series = SharedSeries(
            series = series("n1", "Berserk"),
            volumes = listOf(volume("v1", 1), volume("v2", 2))
        )

        val v = vm()
        advanceUntilIdle()

        val ready = v.uiState.value as SharedSeriesState.Ready
        assertEquals("Berserk", ready.series.series.title)
        assertEquals(listOf(1, 2), ready.series.volumes.map { it.volumeNumber })
        // Chargement ciblé : la collection entière n'est jamais retéléchargée pour ouvrir une série.
        assertEquals(0, repository.loadCount)
        assertEquals(1, repository.seriesCount)
        assertEquals("col-1" to "n1", repository.lastSeriesCall)
    }

    @Test
    fun `serie disparue du partage - acces coupe, pas une erreur reseau`() = runTest {
        repository.series = null

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedSeriesState.Gone, v.uiState.value)
    }

    @Test
    fun `404 - partage revoque ou membre ejecte`() = runTest {
        repository.failure = SharingException(SharingFailure.GONE)

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedSeriesState.Gone, v.uiState.value)
    }

    @Test
    fun `402 - suspension du proprietaire, pas suppression`() = runTest {
        repository.failure = SharingException(SharingFailure.PREMIUM_REQUIRED)

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedSeriesState.Suspended, v.uiState.value)
    }

    @Test
    fun `panne reseau - reessayable`() = runTest {
        repository.failure = SharingException(SharingFailure.NETWORK)

        val v = vm()
        advanceUntilIdle()
        assertEquals(SharedSeriesState.Failed, v.uiState.value)

        repository.failure = null
        repository.series = SharedSeries(series("n1", "Berserk"), emptyList())
        v.retry()
        advanceUntilIdle()

        assertTrue(v.uiState.value is SharedSeriesState.Ready)
    }

    private fun series(id: String, title: String) = Series(
        id = id,
        collectionType = CollectionType.MANGA,
        title = title,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun volume(id: String, number: Int) = Record(
        id = id,
        collectionType = CollectionType.MANGA,
        seriesId = "n1",
        volumeNumber = number,
        artists = emptyList(),
        title = "Berserk T.$number",
        status = ItemStatus.OWNED,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private class FakeRepository : SharedCollectionRepository {
        var series: SharedSeries? = null
        var failure: Throwable? = null
        var loadCount = 0
        var seriesCount = 0
        var lastSeriesCall: Pair<String, String>? = null

        override suspend fun load(
            collectionId: String,
            type: CollectionType
        ): Result<SharedCollectionContent> {
            loadCount++
            return Result.success(SharedCollectionContent(emptyList()))
        }

        override suspend fun item(itemId: String): Result<SharedItemDetail> =
            Result.failure(SharingException(SharingFailure.UNKNOWN))

        override suspend fun series(
            collectionId: String,
            seriesId: String,
            type: CollectionType
        ): Result<SharedSeries?> {
            seriesCount++
            lastSeriesCall = collectionId to seriesId
            return failure?.let { Result.failure(it) } ?: Result.success(series)
        }
    }
}
