package app.acervatim.data.remote.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.acervatim.data.local.db.AcervatimDatabase
import app.acervatim.data.local.db.groupToRecords
import app.acervatim.data.repository.LocalCollectionResolver
import app.acervatim.data.repository.RecordRepositoryImpl
import app.acervatim.domain.model.Artist
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.ItemStatus
import app.acervatim.domain.model.Record
import app.acervatim.domain.model.auth.AuthState
import app.acervatim.domain.model.sync.SyncState
import app.acervatim.domain.model.sync.SyncStatus
import app.acervatim.domain.FakeSettingsRepository
import app.acervatim.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Les deux façons dont le serveur peut refuser d'écrire, et ce que l'app doit en faire.
 *
 * - **409 `technical-limit`** — plafond anti-emballement. Sync *partielle* : on saute, on ne
 *   retente pas, on ne perd rien. A remplacé l'ancien 402 `quota-exceeded` du free tier, disparu
 *   avec les quotas au sprint S2 ; le comportement attendu est inchangé.
 * - **402 `payment-required`** — la sync est premium-only (S2). Etat *terminal* : ni retry, ni
 *   erreur générique, et surtout aucune donnée locale touchée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncManagerLimitTest {

    private lateinit var db: AcervatimDatabase
    private lateinit var api: FakeAcervatimApi
    private lateinit var recordRepository: RecordRepositoryImpl
    private lateinit var syncManager: SyncManagerImpl

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AcervatimDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeAcervatimApi()
        val resolver = LocalCollectionResolver(db.collectionDao(), clock)
        recordRepository = RecordRepositoryImpl(db, db.recordDao(), db.artistDao(), resolver, clock)
        syncManager = SyncManagerImpl(
            api = api,
            database = db,
            collectionDao = db.collectionDao(),
            recordDao = db.recordDao(),
            seriesDao = db.seriesDao(),
            artistDao = db.artistDao(),
            authRepository = FakeAuthRepository(userId = "u1"),
            settingsRepository = FakeSettingsRepository(),
            syncStateStore = SyncStateStore(context).also { it.clear() },
            clock = clock
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `technical limit reached mid-push keeps remaining records dirty and still pulls`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        recordRepository.upsert(vinylRecord("rec-2", "A Love Supreme"))
        api.itemLimit = 1 // le 2e createItem renverra 409 technical-limit

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess) // surtout pas un échec : pas de retry WorkManager
        val status = syncManager.status.value
        assertTrue(status is SyncStatus.LimitReached)
        status as SyncStatus.LimitReached
        // 1 collection + 1 item poussés, 1 item resté local.
        assertEquals(2, status.pushed)
        assertEquals(1, status.skipped)
        // Le pull a bien eu lieu malgré le plafond.
        assertTrue(api.pullCount > 0)

        // Exactement un des deux records est SYNCED, l'autre reste à pousser, intact.
        val entities = listOfNotNull(
            db.recordDao().getEntityById("rec-1"),
            db.recordDao().getEntityById("rec-2")
        )
        assertEquals(2, entities.size)
        val synced = entities.filter { it.syncState == SyncState.SYNCED }
        val pending = entities.filter { it.syncState != SyncState.SYNCED }
        assertEquals(1, synced.size)
        assertEquals(1, pending.size)
        assertNotNull(synced.single().remoteId)
        assertNull(pending.single().remoteId)
    }

    @Test
    fun `collection limit reached skips collection and its items without failing`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        api.collectionLimit = 0 // toute création de collection renvoie 409 technical-limit

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess)
        val status = syncManager.status.value
        assertTrue(status is SyncStatus.LimitReached)
        status as SyncStatus.LimitReached
        assertEquals(0, status.pushed)
        // Seule la collection compte en skipped ; l'item suit le skip silencieux existant
        // (« collection non synchronisée ») et sera compté à la prochaine sync.
        assertEquals(1, status.skipped)
        assertEquals(0, api.createdItems.size)

        val record = db.recordDao().getEntityById("rec-1")
        assertNotNull(record)
        assertNull(record!!.remoteId)
        assertTrue(record.syncState != SyncState.SYNCED)
    }

    @Test
    fun `patches still go through after the create limit is hit`() = runTest {
        // 1re sync sans limite : collection + item créés et SYNCED.
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        assertTrue(syncManager.syncNow().isSuccess)
        assertEquals(1, api.createdItems.size)

        // Modif locale (PATCH attendu) + nouvelle création (bloquée par le plafond).
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue (Remaster)"))
        recordRepository.upsert(vinylRecord("rec-2", "A Love Supreme"))
        api.itemLimit = 1 // déjà 1 création passée → toute nouvelle création 409

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess)
        val status = syncManager.status.value
        assertTrue(status is SyncStatus.LimitReached)
        status as SyncStatus.LimitReached
        assertEquals(1, status.skipped)
        // Le PATCH de rec-1 est passé malgré le plafond de création atteint.
        assertEquals(1, api.patchedItems.size)
        assertEquals(SyncState.SYNCED, db.recordDao().getEntityById("rec-1")!!.syncState)
        assertTrue(db.recordDao().getEntityById("rec-2")!!.syncState != SyncState.SYNCED)
    }

    @Test
    fun `payment required ends in a terminal PremiumRequired state, not a failure`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        api.paymentRequired = true

        val result = syncManager.syncNow()

        // Un 402 n'est pas une panne : pas de Failure, donc pas de bandeau d'erreur ni de retry.
        assertTrue(result.isFailure)
        assertTrue(syncManager.status.value is SyncStatus.PremiumRequired)
    }

    @Test
    fun `payment required leaves local data untouched`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        api.paymentRequired = true

        syncManager.syncNow()

        // L'invariant offline-first : perdre la sync ne retire rien de la collection locale.
        val entity = db.recordDao().getEntityById("rec-1")
        assertNotNull(entity)
        assertNull(entity!!.remoteId)
        assertTrue(entity.syncState != SyncState.SYNCED)
        assertEquals("Kind of Blue", db.recordDao().joinRowsById("rec-1").groupToRecords().first().title)
    }

    @Test
    fun `success status is unchanged when no limit is hit`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess)
        assertTrue(syncManager.status.value is SyncStatus.Success)
    }

    private fun vinylRecord(id: String, title: String): Record = Record(
        id = id,
        collectionType = CollectionType.VINYL,
        artists = listOf(Artist("a1", "Miles Davis")),
        title = title,
        genre = "Jazz",
        year = 1959,
        status = ItemStatus.OWNED,
        createdAt = clock.instant(),
        updatedAt = clock.instant()
    )

    private class FakeAuthRepository(private val userId: String?) : AuthRepository {
        override val authState: StateFlow<AuthState> =
            MutableStateFlow(if (userId != null) AuthState.SignedIn(userId) else AuthState.SignedOut)
        override fun currentUserId(): String? = userId
        override suspend fun signInWithGoogle(activityContext: Context): Result<Unit> = Result.success(Unit)
        override fun signOut() = Unit
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }
}
