package app.acervatim.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Clés d'identité d'une édition : elles doivent rapprocher les graphies d'un même éditeur (scan BnF
 * brut vs libellé de `search/editions`) sans confondre deux marques, ni deux éditions.
 */
class EditionKeyTest {

    /**
     * Vecteurs dont les attendus sont ceux de `normalizePublisher` **du backend**
     * (`edition-records.ts`), calculés en exécutant sa logique le 2026-11-02 : la clé locale d'une
     * série doit égaler celle que le serveur compare, sinon scan et recherche divergent.
     */
    @Test
    fun `cle d'editeur identique a celle du backend`() {
        val vectors = mapOf(
            "Pika éd." to "pika",
            "Pika édition" to "pika",
            "Pika éditeur" to "pika",
            "Pika roman" to "pika roman",
            "Éditions France loisirs" to "france loisirs",
            "L'Harmattan" to "lharmattan",
            "Delcourt-Tonkam" to "delcourt tonkam",
            "@anime [éd., distrib.]" to "@anime distrib",
            "Ki-oon" to "ki oon",
            "Kana (Dargaud-Lombard)" to "kana dargaud lombard"
        )
        vectors.forEach { (raw, expected) -> assertEquals(raw, expected, normalizePublisherKey(raw)) }
    }

    @Test
    fun `les graphies d'un meme editeur se rejoignent`() {
        assertEquals("pika", normalizePublisherKey("Pika éd."))
        assertEquals("pika", normalizePublisherKey("Pika édition"))
        assertEquals("pika", normalizePublisherKey("Pika"))
        assertEquals("france loisirs", normalizePublisherKey("Éditions France loisirs"))
    }

    @Test
    fun `deux marques restent distinctes`() {
        assertNotEquals(normalizePublisherKey("Pika"), normalizePublisherKey("Pika roman"))
        assertNotEquals(normalizePublisherKey("Pika"), normalizePublisherKey("Éditions France loisirs"))
    }

    @Test
    fun `editeur inconnu et edition standard valent la chaine vide`() {
        assertEquals("", normalizePublisherKey(null))
        assertEquals("", normalizePublisherKey("  "))
        assertEquals("", normalizeEditionKey(null))
    }

    @Test
    fun `la mention d'edition perd ses mots generiques comme cote serveur`() {
        assertEquals("colossale", normalizeEditionKey("Éd. colossale"))
        assertEquals("colossale", normalizeEditionKey("Edition colossale"))
        assertEquals("perfect", normalizeEditionKey("Perfect édition"))
        assertNotEquals(normalizeEditionKey("Éd. colossale"), normalizeEditionKey("Éd. limitée"))
    }
}
