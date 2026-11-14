package app.acervatim.data.remote.search

import app.acervatim.data.remote.dto.EditionMappingDto
import app.acervatim.data.remote.dto.EditionTomeDto
import app.acervatim.data.remote.dto.NewTomeDto
import app.acervatim.data.remote.dto.SeriesEditionDto
import app.acervatim.data.remote.dto.SeriesEditionsDto
import app.acervatim.data.remote.dto.SeriesUpdateDto
import app.acervatim.data.remote.dto.SeriesUpdatesDto
import app.acervatim.data.remote.dto.UnifiedItemDto
import app.acervatim.data.remote.sync.FakeAcervatimApi
import app.acervatim.domain.model.ExternalSearchException
import app.acervatim.domain.model.ExternalSearchFailure
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * Projection des endpoints de la recherche de série manga (contrat SD1/SD3) vers le domaine, et
 * paramètres réellement envoyés au serveur.
 */
class SeriesSearchRepositoryTest {

    private val api = FakeAcervatimApi()
    private val repository = ExternalSearchRepositoryImpl(api)

    @Test
    fun `un resultat MangaDex devient un candidat de serie complet`() = runTest {
        api.searchResults = listOf(
            UnifiedItemDto(
                source = "mangadex",
                sourceId = FRIEREN_ID,
                mediaType = "manga",
                title = "Frieren",
                creators = listOf("Yamada Kanehito", "Abe Tsukasa"),
                releaseDate = "2020",
                coverUrl = "https://uploads.mangadex.org/covers/$FRIEREN_ID/c.jpg.512.jpg",
                description = "Après la quête…",
                metadata = buildJsonObject {
                    put("mangaId", FRIEREN_ID)
                    put("titleFr", "Frieren")
                    put("titleRomaji", "Sousou no Frieren")
                    put("malId", "126287")
                    put("anilistId", "118586")
                    put("status", "ongoing")
                    put("num_volumes", 14)
                    put("year", 2020)
                }
            )
        )

        val candidate = repository.searchMangaSeries("frieren").getOrThrow().single()

        assertEquals("manga", api.searchCalls.single().type)
        assertEquals("frieren", api.searchCalls.single().query)
        assertNull(api.searchCalls.single().barcode)
        assertEquals(FRIEREN_ID, candidate.mangaId)
        assertEquals("Sousou no Frieren", candidate.titleRomaji)
        assertEquals(listOf("Yamada Kanehito", "Abe Tsukasa"), candidate.authors)
        assertEquals(2020, candidate.year)
        assertEquals(14, candidate.volumeCount)
        assertEquals("ongoing", candidate.status)
        assertEquals("126287", candidate.malId)
    }

    @Test
    fun `un resultat du repli MAL n'a pas de mangaId et parle le statut MangaDex`() = runTest {
        api.searchResults = listOf(
            UnifiedItemDto(
                source = "mal",
                sourceId = "23390",
                mediaType = "manga",
                title = "Shingeki no Kyojin",
                releaseDate = "2009-09-09",
                metadata = buildJsonObject {
                    put("status", "finished")
                    put("num_volumes", 34)
                }
            )
        )

        val candidate = repository.searchMangaSeries("attaque").getOrThrow().single()

        assertNull(candidate.mangaId)
        assertEquals("23390", candidate.malId)
        assertEquals("completed", candidate.status)
        assertEquals(2009, candidate.year)
    }

    @Test
    fun `un id MangaDex qui n'est pas un UUID n'est pas propage`() = runTest {
        // `search/editions` répondrait 400 : mieux vaut un candidat sans éditions listables.
        api.searchResults = listOf(
            UnifiedItemDto(source = "mangadex", sourceId = "pas-un-uuid", mediaType = "manga", title = "X")
        )

        assertNull(repository.searchMangaSeries("x").getOrThrow().single().mangaId)
    }

    @Test
    fun `403 source-token-required du repli MAL reste actionnable`() = runTest {
        // MangaDex en panne : le serveur replie sur MAL, qui exige un jeton.
        api.searchError = app.acervatim.data.remote.error.ApiException.Forbidden(
            app.acervatim.data.remote.dto.ProblemDetailsDto(
                type = "https://api.acervatim/probs/source-token-required",
                title = "Source token required",
                status = 403,
                provider = "mal"
            )
        )

        val failure = repository.searchMangaSeries("frieren").exceptionOrNull() as ExternalSearchException

        assertEquals(ExternalSearchFailure.SOURCE_TOKEN_REQUIRED, failure.failure)
        assertEquals("mal", failure.provider)
    }

    @Test
    fun `une panne reseau de la recherche de serie devient NETWORK`() = runTest {
        api.searchError = IOException("offline")

        val failure = repository.searchMangaSeries("frieren").exceptionOrNull()

        assertEquals(ExternalSearchFailure.NETWORK, (failure as ExternalSearchException).failure)
    }

    @Test
    fun `les editions repartent avec le mangaId et se projettent telles quelles`() = runTest {
        api.editionsResult = SeriesEditionsDto(
            mangaId = "md-snk",
            editions = listOf(
                SeriesEditionDto(
                    seriesTitleFr = "L'attaque des titans",
                    publisher = "Pika",
                    edition = "Éd. colossale",
                    isArtEdition = true,
                    tomeCount = 12,
                    firstYear = "2015",
                    lastYear = "2023",
                    ongoing = false,
                    hasSourceVolumeRange = true,
                    sampleIsbn = "9782811627000"
                )
            )
        )

        val edition = repository.listEditions("md-snk", title = "attaque").getOrThrow().single()

        assertEquals("md-snk" to "attaque", api.editionsCalls.single())
        assertEquals("L'attaque des titans", edition.seriesTitleFr)
        assertEquals("Pika", edition.publisher)
        assertEquals("Éd. colossale", edition.edition)
        assertTrue(edition.isArtEdition)
        assertTrue(edition.hasSourceVolumeRange)
        assertEquals(12, edition.tomeCount)
    }

    @Test
    fun `une edition sans editeur connu se projette avec un editeur nul`() = runTest {
        api.editionsResult = SeriesEditionsDto(
            editions = listOf(SeriesEditionDto(seriesTitleFr = "Frieren", publisher = null, tomeCount = 3))
        )

        assertNull(repository.listEditions(FRIEREN_ID).getOrThrow().single().publisher)
    }

    @Test
    fun `le JSON reel accepte publisher null et le champ complete`() {
        // Décodé avec la configuration réseau de l'app : un `publisher: null` ou le champ additif
        // `complete` ne doivent pas faire échouer toute la liste.
        val json = app.acervatim.di.NetworkModule.provideJson()
        val dto = json.decodeFromString(
            SeriesEditionsDto.serializer(),
            """{"mangaId":"$FRIEREN_ID","complete":false,"editions":[{"seriesTitleFr":"Frieren","publisher":null,"edition":null,"isArtEdition":false,"tomeCount":3,"firstYear":"2022","lastYear":"2026","ongoing":true,"hasSourceVolumeRange":false,"sampleIsbn":null}]}"""
        )
        assertFalse(dto.complete)
        assertNull(dto.editions.single().publisher)
    }

    @Test
    fun `liste vide mais incomplete - panne retentable, pas aucune edition`() = runTest {
        api.editionsResult = SeriesEditionsDto(editions = emptyList(), complete = false)

        val failure = repository.listEditions(FRIEREN_ID).exceptionOrNull() as ExternalSearchException

        assertEquals(ExternalSearchFailure.NETWORK, failure.failure)
    }

    @Test
    fun `liste incomplete mais non vide - montree quand meme`() = runTest {
        api.editionsResult = SeriesEditionsDto(
            editions = listOf(SeriesEditionDto(seriesTitleFr = "Frieren", publisher = "Ki-oon", tomeCount = 3)),
            complete = false
        )

        assertEquals(1, repository.listEditions(FRIEREN_ID).getOrThrow().size)
    }

    @Test
    fun `502 de la BnF - panne retentable`() = runTest {
        api.editionsError = app.acervatim.data.remote.error.ApiException.Upstream(502, null)

        val failure = repository.listEditions(FRIEREN_ID).exceptionOrNull() as ExternalSearchException

        assertEquals(ExternalSearchFailure.NETWORK, failure.failure)
    }

    @Test
    fun `aucune edition est un succes vide, pas une erreur`() = runTest {
        api.editionsResult = SeriesEditionsDto(mangaId = "md-x", editions = emptyList())

        val result = repository.listEditions("md-x")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertNull(api.editionsCalls.single().second)
    }

    @Test
    fun `edition-mapping envoie editeur, mangaId et since au format AAAAMMJJ`() = runTest {
        api.editionMappingResult = EditionMappingDto(
            titleFr = "Frieren",
            tomeCount = 1,
            tomes = listOf(EditionTomeDto(editionVolume = 15, isbn = "979", noticeCreatedAt = "2026-07-28")),
            publisher = "Ki-oon"
        )

        val mapping = repository.editionMapping(
            titleFr = "Frieren",
            edition = null,
            publisher = "Ki-oon",
            mangaId = "md-frieren",
            since = LocalDate.of(2026, 7, 1)
        ).getOrThrow()

        // Format strict : `2026-07-01` serait ignoré en silence par la BnF (0 notice, aucune erreur).
        assertEquals(listOf("Frieren", null, "Ki-oon", "md-frieren", "20260701"), api.editionMappingCalls.single())
        assertEquals("Ki-oon", mapping.publisher)
        assertEquals(LocalDate.of(2026, 7, 28), mapping.tomes.single().noticeCreatedAt)
    }

    @Test
    fun `une date de notice illisible ne fait pas echouer le mapping`() = runTest {
        api.editionMappingResult = EditionMappingDto(
            titleFr = "Frieren",
            tomeCount = 1,
            tomes = listOf(EditionTomeDto(editionVolume = 1, noticeCreatedAt = "20260728"))
        )

        val mapping = repository.editionMapping("Frieren", edition = null).getOrThrow()

        assertNull(mapping.tomes.single().noticeCreatedAt)
        assertNull(mapping.publisher)
        // Sans paramètres facultatifs, rien n'est envoyé (ancien comportement inchangé).
        assertEquals(listOf("Frieren", null, null, null, null), api.editionMappingCalls.single())
    }

    @Test
    fun `le job d'import porte l'editeur et le mangaId`() = runTest {
        repository.startImportJob("L'attaque des titans", "Éd. colossale", publisher = "Pika", mangaId = "md-snk")

        val body = api.importJobRequests.single()
        assertEquals("Pika", body.publisher)
        assertEquals("md-snk", body.mangaId)
        assertEquals("Éd. colossale", body.edition)
    }

    @Test
    fun `les nouveautes serveur se projettent en tomes d'edition`() = runTest {
        api.seriesUpdatesResult = SeriesUpdatesDto(
            updates = listOf(
                SeriesUpdateDto(
                    nodeId = "node-1",
                    title = "Sakamoto days",
                    publisherFr = "Glénat",
                    newTomes = listOf(
                        NewTomeDto(editionVolume = 22, isbn = "9782344068649", noticeCreatedAt = "2026-07-28")
                    )
                )
            )
        )

        val update = repository.seriesUpdates().getOrThrow().single()

        assertEquals("node-1", update.nodeId)
        assertNull(update.edition)
        assertEquals(22, update.newTomes.single().editionVolume)
        assertEquals(LocalDate.of(2026, 7, 28), update.newTomes.single().noticeCreatedAt)
    }

    @Test
    fun `hors premium les nouveautes serveur echouent sans planter`() = runTest {
        api.seriesUpdatesError = app.acervatim.data.remote.error.ApiException.PaymentRequired(null)

        val result = repository.seriesUpdates()

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is ExternalSearchException)
    }

    private companion object {
        const val FRIEREN_ID = "b0b721ff-c388-4486-aa0f-c2b0bb321512"
    }
}
