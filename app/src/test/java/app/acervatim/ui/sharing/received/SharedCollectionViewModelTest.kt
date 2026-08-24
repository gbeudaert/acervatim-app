package app.acervatim.ui.sharing.received

import androidx.lifecycle.SavedStateHandle
import app.acervatim.data.remote.sharing.SharedCollectionRepositoryImpl
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.ListMode
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.Series
import app.acervatim.domain.model.sharing.ReceivedEntry
import app.acervatim.domain.model.sharing.ReceivedShare
import app.acervatim.domain.model.sharing.SharedItemDetail
import app.acervatim.domain.model.sharing.SharingException
import app.acervatim.domain.model.sharing.SharingFailure
import app.acervatim.domain.repository.SharedCollectionContent
import app.acervatim.domain.repository.SharedCollectionRepository
import app.acervatim.domain.repository.SharedSeries
import app.acervatim.ui.list.CollectionUiState
import app.acervatim.ui.sharing.FakeSharingRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * La vue partagée produit **le même** [CollectionUiState] que les collections locales : c'est ce que
 * ces tests vérifient en priorité, parce que c'est la promesse du sprint — même rendu, mêmes options.
 *
 * Le reste couvre ce qui est propre au partage : la portée affichée, et les trois façons dont un
 * accès s'arrête.
 */
class SharedCollectionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var sharing: FakeSharingRepository
    private lateinit var shared: FakeSharedCollectionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sharing = FakeSharingRepository()
        shared = FakeSharedCollectionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(collectionId: String = "col-1") = SharedCollectionViewModel(
        sharing,
        shared,
        FakeSettingsRepository(),
        clock,
        SavedStateHandle(mapOf(SharedCollectionViewModel.COLLECTION_ID_ARG to collectionId))
    )

    @Test
    fun `rend les memes sections par lettre que les collections locales`() = runTest {
        sharing.received = listOf(received("s1", entry("col-1", setOf(ItemStatus.OWNED))))
        shared.content = SharedCollectionContent(
            records = listOf(
                record("i1", "Kind of Blue", "Miles Davis"),
                record("i2", "OK Computer", "Radiohead"),
                record("i3", "Bitches Brew", "Miles Davis")
            )
        )

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedCollectionContentState.Ready, v.uiState.value.state)
        val flat = v.uiState.value.collection as CollectionUiState.FlatContent
        assertEquals(listOf("M", "R"), flat.sections.map { it.letter })
        // Ordre intra-section : par artiste puis par titre, comme le comparateur partagé.
        assertEquals(
            listOf("Bitches Brew", "Kind of Blue"),
            flat.sections.first().entries.map { it.record.title }
        )
    }

    @Test
    fun `la recherche filtre en memoire, sur titre ou artiste`() = runTest {
        sharing.received = listOf(received("s1", entry("col-1", setOf(ItemStatus.OWNED))))
        shared.content = SharedCollectionContent(
            records = listOf(
                record("i1", "Kind of Blue", "Miles Davis"),
                record("i2", "OK Computer", "Radiohead")
            )
        )
        val v = vm()
        advanceUntilIdle()

        v.onQueryChange("radio")
        val flat = v.uiState.value.collection as CollectionUiState.FlatContent
        assertEquals(listOf("OK Computer"), flat.sections.flatMap { s -> s.entries.map { it.record.title } })

        // Un seul chargement réseau : la recherche ne retourne jamais au serveur.
        assertEquals(1, shared.loadCount)
    }

    @Test
    fun `recherche sans resultat - etat vide filtre, pas une erreur`() = runTest {
        sharing.received = listOf(received("s1", entry("col-1", setOf(ItemStatus.OWNED))))
        shared.content = SharedCollectionContent(records = listOf(record("i1", "Kind of Blue", "Miles Davis")))
        val v = vm()
        advanceUntilIdle()

        v.onQueryChange("zzzz")

        val empty = v.uiState.value.collection as CollectionUiState.Empty
        assertTrue(empty.hasActiveFilters)
    }

    @Test
    fun `les compteurs d'en-tete portent sur la collection entiere, pas sur la recherche`() = runTest {
        sharing.received = listOf(received("s1", entry("col-1", setOf(ItemStatus.OWNED))))
        shared.content = SharedCollectionContent(
            records = listOf(
                record("i1", "Kind of Blue", "Miles Davis"),
                record("i2", "OK Computer", "Radiohead", status = ItemStatus.WISHLIST)
            )
        )
        val v = vm()
        advanceUntilIdle()
        v.onQueryChange("radio")

        val flat = v.uiState.value.collection as CollectionUiState.FlatContent
        assertEquals(1, flat.ownedCount)
        assertEquals(1, flat.wishlistCount)
    }

    @Test
    fun `type serie - mode serie par defaut, comptes derives des tomes visibles`() = runTest {
        sharing.received = listOf(
            received("s1", entry("col-1", setOf(ItemStatus.OWNED), type = "manga"))
        )
        shared.content = SharedCollectionContent(
            records = listOf(
                record("v1", "Berserk T.1", "Miura", seriesId = "n1"),
                record("v2", "Berserk T.2", "Miura", seriesId = "n1", status = ItemStatus.WISHLIST)
            ),
            series = listOf(series("n1", "Berserk"))
        )

        val v = vm()
        advanceUntilIdle()

        assertEquals(ListMode.SERIAL, v.uiState.value.listMode)
        assertTrue(v.uiState.value.supportsSerial)
        val serial = v.uiState.value.collection as CollectionUiState.SerialContent
        val counts = serial.seriesList.single()
        assertEquals(1, counts.ownedVolumes)
        assertEquals(1, counts.wishlistVolumes)
        assertEquals(2, counts.totalVolumesInLibrary)
    }

    @Test
    fun `bascule serie vers plate sans retourner au reseau`() = runTest {
        sharing.received = listOf(
            received("s1", entry("col-1", setOf(ItemStatus.OWNED), type = "manga"))
        )
        shared.content = SharedCollectionContent(
            records = listOf(record("v1", "Berserk T.1", "Miura", seriesId = "n1")),
            series = listOf(series("n1", "Berserk"))
        )
        val v = vm()
        advanceUntilIdle()

        v.onListModeChange(ListMode.FLAT)

        assertTrue(v.uiState.value.collection is CollectionUiState.FlatContent)
        assertEquals(1, shared.loadCount)
    }

    @Test
    fun `deux partages de la meme collection - on voit l'union des statuts`() = runTest {
        sharing.received = listOf(
            received("s1", entry("col-1", setOf(ItemStatus.OWNED))),
            received("s2", entry("col-1", setOf(ItemStatus.WISHLIST)))
        )
        val v = vm()
        advanceUntilIdle()

        assertEquals(setOf(ItemStatus.OWNED, ItemStatus.WISHLIST), v.uiState.value.statuses)
    }

    @Test
    fun `collection absente des partages recus - acces coupe`() = runTest {
        sharing.received = listOf(received("s1", entry("col-autre", setOf(ItemStatus.OWNED))))

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedCollectionContentState.Gone, v.uiState.value.state)
        assertEquals(0, shared.loadCount)
    }

    @Test
    fun `402 - suspension du proprietaire, pas suppression`() = runTest {
        sharing.received = listOf(received("s1", entry("col-1", setOf(ItemStatus.OWNED))))
        shared.failure = SharingException(SharingFailure.PREMIUM_REQUIRED)

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedCollectionContentState.Suspended, v.uiState.value.state)
    }

    @Test
    fun `404 en cours de lecture - partage revoque`() = runTest {
        sharing.received = listOf(received("s1", entry("col-1", setOf(ItemStatus.OWNED))))
        shared.failure = SharingException(SharingFailure.GONE)

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedCollectionContentState.Gone, v.uiState.value.state)
    }

    @Test
    fun `panne reseau - reessayable, distinct d'un acces coupe`() = runTest {
        sharing.received = listOf(received("s1", entry("col-1", setOf(ItemStatus.OWNED))))
        shared.failure = SharingException(SharingFailure.NETWORK)

        val v = vm()
        advanceUntilIdle()

        assertEquals(SharedCollectionContentState.Failed, v.uiState.value.state)
    }

    /**
     * Garde-fou structurel : la lecture partagée ne doit dépendre **que** du réseau.
     *
     * Le jour où quelqu'un y branche un DAO « pour aller plus vite », ces collections entrent dans
     * Room, donc dans l'export et la sauvegarde ZIP de l'utilisateur — alors qu'elles ne sont pas ses
     * données et peuvent disparaître à tout moment. Le test échoue avant que ça n'arrive.
     */
    @Test
    fun `la lecture partagee ne depend d'aucun stockage local`() {
        val params = SharedCollectionRepositoryImpl::class.java
            .declaredConstructors
            .single()
            .parameterTypes
            .map { it.name }

        assertTrue(
            "dépendances inattendues : $params",
            params.none { it.contains("Dao") || it.contains("Database") || it.contains("DataStore") }
        )
    }

    private fun record(
        id: String,
        title: String,
        artist: String,
        seriesId: String? = null,
        status: ItemStatus = ItemStatus.OWNED
    ) = Record(
        id = id,
        collectionType = if (seriesId != null) CollectionType.MANGA else CollectionType.VINYL,
        seriesId = seriesId,
        artists = listOf(Artist(id = "a-$artist", name = artist)),
        title = title,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun series(id: String, title: String) = Series(
        id = id,
        collectionType = CollectionType.MANGA,
        title = title,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun entry(collectionId: String, statuses: Set<ItemStatus>, type: String = "vinyl") =
        ReceivedEntry(
            collectionId = collectionId,
            name = "Collection de Bob",
            typeCode = type,
            statuses = statuses,
            itemCount = 1
        )

    private fun received(id: String, vararg entries: ReceivedEntry) = ReceivedShare(
        shareId = id,
        label = null,
        redeemedAt = Instant.parse("2026-08-22T10:00:00Z"),
        collections = entries.toList()
    )

    private class FakeSharedCollectionRepository : SharedCollectionRepository {
        var content: SharedCollectionContent = SharedCollectionContent(emptyList())
        var failure: Throwable? = null
        var loadCount = 0

        override suspend fun load(
            collectionId: String,
            type: CollectionType
        ): Result<SharedCollectionContent> {
            loadCount++
            return failure?.let { Result.failure(it) } ?: Result.success(content)
        }

        override suspend fun item(itemId: String): Result<SharedItemDetail> =
            Result.failure(SharingException(SharingFailure.UNKNOWN))

        // Le detail d'une serie a son propre test : ici il n'est jamais appele.
        override suspend fun series(
            collectionId: String,
            seriesId: String,
            type: CollectionType
        ): Result<SharedSeries?> = Result.success(null)
    }
}
