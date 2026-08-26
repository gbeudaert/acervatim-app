package app.acervatim.data.remote.search

import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.domain.model.AcervatimFormat
import app.acervatim.domain.model.RecordingSpeed
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Couvre le trou de mapping qui laissait `genre`/`format`/`recordingSpeed` d'un vinyle scanné hors du
 * pré-remplissage : le backend les remonte bien dans `metadata` (Discogs), l'app doit les extraire.
 */
class ExternalSearchMapperTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun decode(raw: String): UnifiedItemDto =
        json.decodeFromString(UnifiedItemDto.serializer(), raw)

    @Test
    fun `extrait genre format et vitesse d'un resultat vinyle Discogs`() {
        // Métadonnées telles que produites par mapSearchResult côté backend (Discogs adapter).
        val dto = decode(
            """
            {
              "source":"discogs","sourceId":"249504","mediaType":"vinyl",
              "title":"Rick Astley Album","creators":["Rick Astley"],
              "releaseDate":"1987-01-01","coverUrl":"https://img/cover.jpg",
              "metadata":{
                "format":["Vinyl","LP","Album","33 ⅓ RPM"],
                "genres":["Electronic","Pop"],
                "styles":["Synth-pop"],
                "recordingSpeed":"RPM_33",
                "label":["RCA"],"country":"UK"
              }
            }
            """.trimIndent()
        )

        val match = dto.toExternalMatch()

        assertEquals("Electronic", match.genre)
        assertEquals(AcervatimFormat.LP_33, match.format)
        assertEquals(RecordingSpeed.RPM_33, match.recordingSpeed)
    }

    @Test
    fun `format single derive du token 7 pouces`() {
        val dto = decode(
            """
            {"source":"discogs","sourceId":"1","mediaType":"vinyl","title":"45t",
             "metadata":{"format":["Vinyl","7\"","45 RPM","Single"],"recordingSpeed":"RPM_45"}}
            """.trimIndent()
        )

        val match = dto.toExternalMatch()

        assertEquals(AcervatimFormat.SINGLE_45, match.format)
        assertEquals(RecordingSpeed.RPM_45, match.recordingSpeed)
    }

    @Test
    fun `metadata absente laisse les champs vinyle a null`() {
        val dto = decode(
            """{"source":"mal","sourceId":"9","mediaType":"manga","title":"Sans metadata"}"""
        )

        val match = dto.toExternalMatch()

        assertNull(match.genre)
        assertNull(match.format)
        assertNull(match.recordingSpeed)
        assertNull(match.label)
        assertNull(match.country)
        assertNull(match.catalogNumber)
        assertNull(match.masterId)
        assertEquals(emptyList<String>(), match.barcodes)
    }

    @Test
    fun `extrait les discriminants d'un pressage (SD1)`() {
        // Sans ces champs, deux pressages d'un même album s'affichent en lignes identiques : c'est
        // exactement ce que la recherche par titre doit permettre de départager.
        val dto = decode(
            """
            {
              "source":"discogs","sourceId":"10432","mediaType":"vinyl",
              "title":"Degiheugi - Endless Smile","creators":["Degiheugi"],
              "releaseDate":"2017-01-01",
              "metadata":{
                "label":["Jarring Effects","JarringFX"],
                "country":"France",
                "catno":"ENLP07",
                "barcodes":["3521381542899"],
                "masterId":1234567
              }
            }
            """.trimIndent()
        )

        val match = dto.toExternalMatch()

        assertEquals("Jarring Effects", match.label)
        assertEquals("France", match.country)
        assertEquals("ENLP07", match.catalogNumber)
        assertEquals(listOf("3521381542899"), match.barcodes)
        // `master_id` est un nombre chez Discogs : l'app n'en fait qu'une clé de regroupement.
        assertEquals("1234567", match.masterId)
    }
}
