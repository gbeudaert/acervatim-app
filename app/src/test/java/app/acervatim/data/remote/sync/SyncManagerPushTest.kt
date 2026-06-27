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
import app.acervatim.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncManagerPushTest {

    private lateinit var db: AcervatimDatabase
    private lateinit var api: FakeAcervatimApi
    private lateinit var recordRepository: RecordRepositoryImpl
    private lateinit var syncStateStore: SyncStateStore
    private lateinit var syncManager: SyncManagerImpl

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AcervatimDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        api = FakeAcervatimApi()
        syncStateStore = SyncStateStore(context).also { it.clear() }
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
            syncStateStore = syncStateStore,
            clock = clock
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `push creates collection then item and marks the record SYNCED`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1"))

        val result = syncManager.syncNow()

        assertTrue(result.isSuccess)
        // Une collection vinyle créée, un item créé.
        assertEquals(1, api.createdCollections.size)
        assertEquals("vinyl", api.createdCollections.first().typeCode)
        assertEquals(1, api.createdItems.size)
        // unifiedData du push contient bien le titre curé.
        assertTrue(api.createdItems.first().unifiedData.toString().contains("Kind of Blue"))

        val entity = db.recordDao().getEntityById("rec-1")
        assertNotNull(entity)
        assertEquals(SyncState.SYNCED, entity!!.syncState)
        assertNotNull(entity.remoteId)
        assertTrue(syncManager.status.value is SyncStatus.Success)
    }

    @Test
    fun `sync without session fails fast`() = runTest {
        val offline = SyncManagerImpl(
            api, db, db.collectionDao(), db.recordDao(), db.seriesDao(), db.artistDao(),
            FakeAuthRepository(userId = null), syncStateStore, clock
        )
        val result = offline.syncNow()
        assertTrue(result.isFailure)
    }

    @Test
    fun `last sync date survives a fresh SyncManager backed by the same store`() = runTest {
        recordRepository.upsert(vinylRecord("rec-1"))
        assertTrue(syncManager.syncNow().isSuccess)

        // Simule un redémarrage : nouvelle instance lisant le même store persistant.
        val restarted = SyncManagerImpl(
            api, db, db.collectionDao(), db.recordDao(), db.seriesDao(), db.artistDao(),
            FakeAuthRepository(userId = "u1"), SyncStateStore(ApplicationProvider.getApplicationContext()), clock
        )
        val status = restarted.status.value
        assertTrue(status is SyncStatus.Success)
        assertEquals(clock.instant(), (status as SyncStatus.Success).at)
    }

    private fun vinylRecord(id: String): Record = Record(
        id = id,
        collectionType = CollectionType.VINYL,
        artists = listOf(Artist("a1", "Miles Davis")),
        title = "Kind of Blue",
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
