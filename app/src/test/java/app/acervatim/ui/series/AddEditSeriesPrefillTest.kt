package app.acervatim.ui.series

import androidx.lifecycle.SavedStateHandle
import app.acervatim.domain.FakeRecordRepository
import app.acervatim.domain.FakeSeriesRepository
import app.acervatim.domain.repository.CoverStorage
import app.acervatim.domain.usecase.ObserveSeriesByIdUseCase
import app.acervatim.domain.usecase.UpsertSeriesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock

/**
 * Pré-remplissage de la création manuelle de série — le repli de la recherche par titre quand la
 * BnF ne connaît aucune édition française : ce que la recherche a appris n'est pas à retaper.
 */
class AddEditSeriesPrefillTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var series: FakeSeriesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        series = FakeSeriesRepository(FakeRecordRepository())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(args: Map<String, Any?>) = AddEditSeriesViewModel(
        savedStateHandle = SavedStateHandle(args),
        observeSeries = ObserveSeriesByIdUseCase(series),
        upsertSeries = UpsertSeriesUseCase(series, Clock.systemUTC()),
        coverStorage = NoCovers
    )

    @Test
    fun `la creation est pre-remplie avec le titre et l'auteur transmis`() {
        val v = vm(
            mapOf(
                "collectionType" to "MANGA",
                "prefillTitle" to " Sousou no Frieren ",
                "prefillAuthor" to "Yamada Kanehito"
            )
        )

        assertEquals("Sousou no Frieren", v.formState.value.title)
        assertEquals("Yamada Kanehito", v.formState.value.originalAuthor)
    }

    @Test
    fun `sans pre-remplissage le formulaire reste vide`() {
        val v = vm(mapOf("collectionType" to "MANGA"))

        assertEquals("", v.formState.value.title)
        assertEquals("", v.formState.value.originalAuthor)
    }

    @Test
    fun `en edition le pre-remplissage est ignore`() {
        val v = vm(mapOf("seriesId" to "s-1", "prefillTitle" to "Autre titre"))

        assertEquals("", v.formState.value.title)
    }
}

private object NoCovers : CoverStorage {
    override suspend fun saveFromUri(recordId: String, uriString: String): String = pathFor(recordId)
    override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String = pathFor(recordId)
    override suspend fun saveFromUrl(recordId: String, url: String): String = pathFor(recordId)
    override suspend fun delete(recordId: String) = Unit
    override fun pathFor(recordId: String): String = "/fake/covers/$recordId.jpg"
}
