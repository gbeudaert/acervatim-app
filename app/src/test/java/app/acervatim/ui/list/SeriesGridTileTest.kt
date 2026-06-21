package app.acervatim.ui.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.SeriesWithCounts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Tests UI Compose (Robolectric) de la tuile de la vue SERIAL. C'est l'unité visuelle qui affiche
 * les résultats de la recherche par séries (Sprint 9, cf. [ObserveSeriesWithCountsUseCaseTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeriesGridTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-05-19T08:00:00Z")

    private fun item(): SeriesWithCounts = SeriesWithCounts(
        series = Series(
            id = "s1",
            collectionType = CollectionType.MANGA,
            title = "Berserk",
            originalAuthor = "Kentarō Miura",
            totalVolumes = 42,
            coverPath = null,
            createdAt = now,
            updatedAt = now
        ),
        ownedVolumes = 28,
        wishlistVolumes = 10,
        ignoredVolumes = 0,
        totalVolumesInLibrary = 38
    )

    @Test
    fun `tile displays series title and volume count`() {
        composeRule.setContent {
            MaterialTheme {
                SeriesGridTile(item = item(), onClick = {})
            }
        }

        composeRule.onNodeWithText("Berserk").assertIsDisplayed()
        // Compteur "28 / 42" (owned / total annoncé) — substring match.
        composeRule.onNodeWithText("28", substring = true).assertIsDisplayed()
    }

    @Test
    fun `clicking the tile invokes onClick`() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                SeriesGridTile(item = item(), onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("Berserk").performClick()
        assertEquals(true, clicked)
    }
}
