package app.acervatim.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le regroupement est ce qui rend une recherche texte exploitable : sans lui, vingt pressages d'un
 * même album s'alignent en vingt lignes indiscernables.
 */
class PressingGroupTest {

    private fun pressing(
        sourceId: String,
        masterId: String? = null,
        year: Int? = null,
        coverUrl: String? = null,
        title: String = "Endless Smile",
        creators: List<String> = listOf("Degiheugi")
    ) = ExternalMatch(
        source = "discogs",
        sourceId = sourceId,
        title = title,
        creators = creators,
        year = year,
        coverUrl = coverUrl,
        masterId = masterId
    )

    @Test
    fun `les pressages d'un meme master forment un seul groupe`() {
        val groups = listOf(
            pressing(sourceId = "1", masterId = "42", year = 2017),
            pressing(sourceId = "2", masterId = "42", year = 2021),
            pressing(sourceId = "3", masterId = "99", year = 2010, title = "Autre album")
        ).groupPressings()

        assertEquals(2, groups.size)
        assertEquals(listOf("1", "2"), groups[0].pressings.map { it.sourceId })
        assertEquals(listOf("3"), groups[1].pressings.map { it.sourceId })
    }

    @Test
    fun `un pressage sans master reste seul dans son groupe`() {
        // Sans master déclaré, rien ne prouve que deux titres identiques désignent le même album :
        // les regrouper rapprocherait des homonymes.
        val groups = listOf(
            pressing(sourceId = "1"),
            pressing(sourceId = "2")
        ).groupPressings()

        assertEquals(2, groups.size)
        assertEquals(listOf(true, true), groups.map { it.isSingle })
    }

    @Test
    fun `les pressages d'un groupe sont tries par annee, inconnue en dernier`() {
        val group = listOf(
            pressing(sourceId = "sans-annee", masterId = "42"),
            pressing(sourceId = "2021", masterId = "42", year = 2021),
            pressing(sourceId = "1997", masterId = "42", year = 1997)
        ).groupPressings().single()

        assertEquals(listOf("1997", "2021", "sans-annee"), group.pressings.map { it.sourceId })
    }

    @Test
    fun `la vignette du groupe vient du resultat le plus pertinent, la jaquette du premier qui en a une`() {
        // L'ordre reçu du serveur est celui de la pertinence Discogs : c'est lui qui nomme le
        // groupe, et non le tri par année appliqué à l'intérieur.
        val group = listOf(
            pressing(sourceId = "1", masterId = "42", year = 2021, title = "Endless Smile"),
            pressing(
                sourceId = "2",
                masterId = "42",
                year = 1997,
                coverUrl = "https://img/cover.jpg",
                title = "Endless Smile (Reissue)"
            )
        ).groupPressings().single()

        assertEquals("Endless Smile", group.title)
        assertEquals("https://img/cover.jpg", group.coverUrl)
        assertEquals("2", group.pressings.first().sourceId)
    }

    @Test
    fun `l'ordre des groupes suit la pertinence du serveur`() {
        val groups = listOf(
            pressing(sourceId = "1", masterId = "7", title = "Le plus pertinent"),
            pressing(sourceId = "2", masterId = "8", title = "Le suivant"),
            pressing(sourceId = "3", masterId = "7", title = "Le plus pertinent (autre pressage)")
        ).groupPressings()

        assertEquals(listOf("Le plus pertinent", "Le suivant"), groups.map { it.title })
    }

    @Test
    fun `une liste vide ne produit aucun groupe`() {
        assertEquals(emptyList<PressingGroup>(), emptyList<ExternalMatch>().groupPressings())
    }
}
