package app.acervatim.ui.series

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.acervatim.R
import app.acervatim.domain.model.CollectionType
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
    private val editLabel: String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.action_edit)

    private val loaded = SeriesDetailUiState.Loaded(
        series = Series(
            id = "s1",
            collectionType = CollectionType.MANGA,
            title = "Berserk",
            originalAuthor = "Kentarō Miura",
            coverPath = null,
            createdAt = now,
            updatedAt = now
        ),
        volumes = emptyList()
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

        composeRule.onNodeWithContentDescription(editLabel)
            .assertHasClickAction()
            .performClick()
        assertEquals("s1", editedId)
    }

    @Test
    fun `edit action is hidden while loading`() {
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

        composeRule.onNodeWithContentDescription(editLabel).assertDoesNotExist()
    }
}
