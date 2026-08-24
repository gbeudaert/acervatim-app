package app.acervatim.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration v6 → v7 (compteur d'écoutes) sur une base **existante** : la colonne s'ajoute, les
 * lignes déjà là survivent, et elles repartent de 0.
 *
 * Le test construit la table v6 à la main plutôt que d'utiliser `MigrationTestHelper` : ce dernier
 * demanderait `androidx.room:room-testing`, et le dépôt n'ajoute pas de dépendance sans accord.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration6To7Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(DB_NAME)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(CREATE_RECORDS_V6)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() = helper.close()

    @Test
    fun `la colonne playCount arrive a zero sans perdre les disques existants`() {
        db.execSQL(
            """
            INSERT INTO records (id, collectionType, title, status, extras, createdAt, updatedAt,
                                 collectionId, syncState, lastPlayedAt)
            VALUES ('rec-1', 'VINYL', 'Kind of Blue', 'OWNED', '{}', 1, 1, '', 'LOCAL_ONLY', 42)
            """.trimIndent()
        )

        MIGRATION_6_7.migrate(db)

        db.query("SELECT title, lastPlayedAt, playCount FROM records WHERE id = 'rec-1'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Kind of Blue", c.getString(0))
            // L'horodatage survit ; le compteur, lui, n'a rien à reprendre — `lastPlayedAt` ne dit
            // pas *combien de fois*. Repartir de 0 est assumé (cf. sprint AD3).
            assertEquals(42L, c.getLong(1))
            assertEquals(0, c.getInt(2))
        }
    }

    private companion object {
        const val DB_NAME = "migration-6-7-test.db"

        /** Sous-ensemble de la table v6 suffisant pour la migration testée. */
        val CREATE_RECORDS_V6 = """
            CREATE TABLE IF NOT EXISTS records (
                id TEXT NOT NULL PRIMARY KEY,
                collectionType TEXT NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                lastPlayedAt INTEGER,
                extras TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                collectionId TEXT NOT NULL,
                remoteId TEXT,
                syncState TEXT NOT NULL,
                serverUpdatedAt INTEGER
            )
        """.trimIndent()
    }
}
