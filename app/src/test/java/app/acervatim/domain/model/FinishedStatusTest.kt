package app.acervatim.domain.model

import app.acervatim.data.remote.mapper.FacadeMapper
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * « Terminé » : une seule règle ([isFinishedStatus]) pour le rattrapage et pour la synchro. Les deux
 * avaient chacun leur liste et divergeaient — « Terminé » arrêtait le rattrapage mais partait
 * `unknown` au serveur.
 */
class FinishedStatusTest {

    private val finishedForms = listOf("Terminé", "Terminée", "TERMINÉE", "Terminee", "termine", " finished ", "completed")

    @Test
    fun `toutes les formes de termine sont reconnues, casse et accents plies`() {
        finishedForms.forEach { assertTrue(it, isFinishedStatus(it)) }
    }

    @Test
    fun `les autres statuts ne sont pas termines`() {
        listOf(null, "", "En cours", "ongoing", "hiatus", "Terminus").forEach {
            assertFalse(it.toString(), isFinishedStatus(it))
        }
    }

    @Test
    fun `rattrapage et synchro disent la meme chose`() {
        for (form in finishedForms + listOf("En cours", "en cours", "Ongoing", "pause", "???")) {
            val series = Series(
                id = "s",
                collectionType = CollectionType.MANGA,
                title = "T",
                publicationStatus = form,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH
            )
            val code = FacadeMapper.seriesToRemote(series, "col").node.unifiedData["status"]!!.jsonPrimitive.content
            assertEquals(form, series.isPublicationFinished, code == "finished")
        }
    }

    @Test
    fun `les autres statuts gardent leur code de synchro, plies eux aussi`() {
        fun code(status: String) = FacadeMapper.seriesToRemote(
            Series(
                id = "s",
                collectionType = CollectionType.MANGA,
                title = "T",
                publicationStatus = status,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH
            ),
            "col"
        ).node.unifiedData["status"]!!.jsonPrimitive.content

        assertEquals("ongoing", code("En cours"))
        assertEquals("ongoing", code("EN COURS"))
        assertEquals("hiatus", code("Pause"))
        assertEquals("unknown", code("Bientôt"))
    }
}
