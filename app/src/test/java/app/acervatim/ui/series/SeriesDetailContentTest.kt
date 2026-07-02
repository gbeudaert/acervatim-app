package app.acervatim.ui.series

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.acervatim.R
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Tests UI Compose (Robolectric) du point d'entrée d'édition de série (Sprint 9 — dette héritée
 * des Sprints 7-8 : la route EDIT_SERIES existait sans bouton UI pour l'atteindre).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeriesDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-05-19T08:00:00Z")
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val editLabel: String = context.getString(R.string.action_edit)
    private val moreLabel: String = context.getString(R.string.action_more)
    private val deleteLabel: String = context.getString(R.string.action_delete)

    private val series = Series(
        id = "s1",
        collectionType = CollectionType.MANGA,
        title = "Berserk",
        originalAuthor = "Kentarō Miura",
        coverPath = null,
        createdAt = now,
        updatedAt = now
    )

    private val loaded = SeriesDetailUiState.Loaded(series = series, volumes = emptyList())

    private fun loadedWithTomes(count: Int) = SeriesDetailUiState.Loaded(
        series = series,
        volumes = (1..count).map { i ->
            Record(
                id = "t$i",
                collectionType = CollectionType.MANGA,
                seriesId = "s1",
                volumeNumber = i,
                artists = listOf(Artist(id = "", name = "Kentarō Miura")),
                title = "Tome $i",
                status = ItemStatus.OWNED,
                createdAt = now,
                updatedAt = now
            )
        }
    )

    @Test
    fun `edit action is shown and forwards the series id when loaded`() {
        var editedId: String? = null
        composeRule.setContent {
            MaterialTheme {
                SeriesDetailContent(
                    state = loaded,
                    onBack = {},
                    onOpenVolume = {},
                    onAddVolume = { _, _ -> },
                    onEditSeries = { editedId = it },
                    onSetStatus = { _, _ -> }
                )
            }
        }

        // L'édition vit désormais dans le menu overflow : on l'ouvre puis on clique l'entrée.
        composeRule.onNodeWithContentDescription(moreLabel).performClick()
        composeRule.onNodeWithText(editLabel)
            .assertHasClickAction()
            .performClick()
        assertEquals("s1", editedId)
    }

    @Test
    fun `overflow menu is hidden while loading`() {
        composeRule.setContent {
            MaterialTheme {
                SeriesDetailContent(
                    state = SeriesDetailUiState.Loading,
                    onBack = {},
                    onOpenVolume = {},
                    onAddVolume = { _, _ -> },
                    onEditSeries = {},
                    onSetStatus = { _, _ -> }
                )
            }
        }

        // Tant que la série n'est pas chargée, le déclencheur de menu (et donc l'édition) est absent.
        composeRule.onNodeWithContentDescription(moreLabel).assertDoesNotExist()
    }

    @Test
    fun `delete dialog offers cascade and orphan modes when the series has tomes`() {
        var deletedCascade: Boolean? = null
        composeRule.setContent {
            MaterialTheme {
                SeriesDetailContent(
                    state = loadedWithTomes(2),
                    onBack = {},
                    onOpenVolume = {},
                    onAddVolume = { _, _ -> },
                    onEditSeries = {},
                    onSetStatus = { _, _ -> },
                    onDelete = { deletedCascade = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(moreLabel).performClick()
        composeRule.onNodeWithText(deleteLabel).performClick()

        val cascadeLabel = context.resources.getQuantityString(R.plurals.series_delete_cascade, 2, 2)
        val orphanLabel = context.getString(R.string.series_delete_orphan)
        composeRule.onNodeWithText(orphanLabel).assertHasClickAction()
        composeRule.onNodeWithText(cascadeLabel).performClick()

        assertEquals(true, deletedCascade)
    }

    @Test
    fun `delete dialog orphan choice deletes only the node`() {
        var deletedCascade: Boolean? = null
        composeRule.setContent {
            MaterialTheme {
                SeriesDetailContent(
                    state = loadedWithTomes(3),
                    onBack = {},
                    onOpenVolume = {},
                    onAddVolume = { _, _ -> },
                    onEditSeries = {},
                    onSetStatus = { _, _ -> },
                    onDelete = { deletedCascade = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(moreLabel).performClick()
        composeRule.onNodeWithText(deleteLabel).performClick()
        composeRule.onNodeWithText(context.getString(R.string.series_delete_orphan)).performClick()

        assertEquals(false, deletedCascade)
    }
}
