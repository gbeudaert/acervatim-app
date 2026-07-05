package app.acervatim.data.remote.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.acervatim.data.local.db.AcervatimDatabase
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

/** Sync partielle : un 402 quota-exceeded ne doit jamais faire échouer la sync (cf. plan §2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncManagerQuotaTest {

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
    fun `item quota reached mid-push keeps remaining records dirty and still pulls`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        recordRepository.upsert(vinylRecord("rec-2", "A Love Supreme"))
        api.itemQuotaLimit = 1 // le 2e createItem renverra 402

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess) // surtout pas un échec : pas de retry WorkManager
        val status = syncManager.status.value
        assertTrue(status is SyncStatus.QuotaReached)
        status as SyncStatus.QuotaReached
        // 1 collection + 1 item poussés, 1 item resté local.
        assertEquals(2, status.pushed)
        assertEquals(1, status.skipped)
        // Le pull a bien eu lieu malgré le quota.
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
    fun `collection quota reached skips collection and its items without failing`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        api.collectionQuotaLimit = 0 // toute création de collection renvoie 402

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess)
        val status = syncManager.status.value
        assertTrue(status is SyncStatus.QuotaReached)
        status as SyncStatus.QuotaReached
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
    fun `patches still go through after the create quota is hit`() = runTest {
        // 1re sync sans limite : collection + item créés et SYNCED.
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue"))
        assertTrue(syncManager.syncNow().isSuccess)
        assertEquals(1, api.createdItems.size)

        // Modif locale (PATCH attendu) + nouvelle création (bloquée par quota).
        recordRepository.upsert(vinylRecord("rec-1", "Kind of Blue (Remaster)"))
        recordRepository.upsert(vinylRecord("rec-2", "A Love Supreme"))
        api.itemQuotaLimit = 1 // déjà 1 création passée → toute nouvelle création 402

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess)
        val status = syncManager.status.value
        assertTrue(status is SyncStatus.QuotaReached)
        status as SyncStatus.QuotaReached
        assertEquals(1, status.skipped)
        // Le PATCH de rec-1 est passé malgré le quota de création atteint.
        assertEquals(1, api.patchedItems.size)
        assertEquals(SyncState.SYNCED, db.recordDao().getEntityById("rec-1")!!.syncState)
        assertTrue(db.recordDao().getEntityById("rec-2")!!.syncState != SyncState.SYNCED)
    }

    @Test
    fun `success status is unchanged when no quota is hit`() = runTest {
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
