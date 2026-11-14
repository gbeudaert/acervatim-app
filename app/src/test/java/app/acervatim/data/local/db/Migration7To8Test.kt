package app.acervatim.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.acervatim.domain.model.CollectionType
import app.acervatim.domain.model.RecordFilter
import app.acervatim.domain.model.RecordSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migration v7 → v8 sur une base v7 **complète**, construite depuis le schéma exporté
 * (`schemas/…/7.json`), puis ouverte par Room avec [MIGRATION_7_8] et **sans** repli destructif.
 *
 * C'est ce qui manquait au test v6 → v7 (table construite à la main) : ici Room revalide tout le
 * schéma après la migration — colonnes, `NOT NULL`, index unique de clé naturelle — et lève une
 * `IllegalStateException` au moindre écart avec les entités. Sans `MigrationTestHelper`, dont les
 * assets de schéma ne sont pas câblés dans les tests JVM du dépôt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration7To8Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: AcervatimDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
        createV7Database()
    }

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `Room valide le schema migre et backfille normalized_title`() = runTest {
        val db = openMigrated()

        db.openHelper.readableDatabase
            .query("SELECT normalized_title FROM records WHERE id = 'rec-1'")
            .use { c ->
                c.moveToFirst()
                assertEquals("ete indien", c.getString(0))
            }
    }

    @Test
    fun `la recherche de la liste plate trouve un titre accentue migre`() = runTest {
        val db = openMigrated()

        val rows = db.recordDao().observeFilteredJoinRows(
            RecordQueries.filteredJoinRows(RecordFilter(query = "ete"), RecordSort.DEFAULT, null)
        ).first()

        assertEquals(listOf("rec-1"), rows.map { it.record.id }.distinct())
    }

    @Test
    fun `une serie d'avant la v8 garde sa cle, avec editeur et edition vides`() = runTest {
        val db = openMigrated()

        val legacy = db.seriesDao().findByNaturalKey(
            collectionType = CollectionType.MANGA,
            normalizedTitle = normalizeSeriesKey("L'attaque des titans"),
            normalizedAuthor = normalizeSeriesKey("Hajime Isayama")
        )

        assertNotNull(legacy)
        assertEquals("", legacy!!.normalizedPublisher)
        assertEquals("", legacy.normalizedEdition)
    }

    @Test
    fun `apres migration deux editions coexistent mais un doublon exact est refuse`() = runTest {
        val sql = openMigrated().openHelper.writableDatabase

        // Même série, autre éditeur : la nouvelle clé l'admet (c'était un 409 côté serveur).
        sql.execSQL(insertSeries(id = "s-fl", normalizedPublisher = "france loisirs"))

        // Même clé complète que la série existante : l'index unique tient toujours.
        assertThrows(SQLiteConstraintException::class.java) {
            sql.execSQL(insertSeries(id = "s-dup", normalizedPublisher = ""))
        }
    }

    private fun openMigrated(): AcervatimDatabase =
        Room.databaseBuilder(context, AcervatimDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
            .also {
                room = it
                // Ouvrir déclenche la migration puis la validation du schéma par Room.
                it.openHelper.writableDatabase
            }

    /** Base v7 telle que Room la créait : tables, index et table maître lus dans `7.json`. */
    private fun createV7Database() {
        val database = Json.parseToJsonElement(File(SCHEMA_V7).readText())
            .jsonObject.getValue("database").jsonObject
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (entity in database.getValue("entities").jsonArray) {
                        val e = entity.jsonObject
                        val table = e.getValue("tableName").jsonPrimitive.content
                        db.execSQL(e.getValue("createSql").jsonPrimitive.content.withTable(table))
                        e["indices"]?.jsonArray?.forEach { index ->
                            db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.withTable(table))
                        }
                    }
                    database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                    seedV7(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase
            close()
        }
    }

    private fun seedV7(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO series (id, collectionType, title, originalAuthor, normalizedTitle,
                                normalizedAuthor, createdAt, updatedAt, collectionId, syncState)
            VALUES ('s-1', 'MANGA', 'L''attaque des titans', 'Hajime Isayama',
                    '${normalizeSeriesKey("L'attaque des titans").replace("'", "''")}',
                    'hajime isayama', 1, 1, '', 'LOCAL_ONLY')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO records (id, collectionType, title, status, playCount, extras, createdAt,
                                 updatedAt, collectionId, syncState)
            VALUES ('rec-1', 'VINYL', 'Été indien', 'OWNED', 0, '{}', 1, 1, '', 'LOCAL_ONLY')
            """.trimIndent()
        )
        db.execSQL("INSERT INTO artists (id, name, normalized_name) VALUES ('a-1', 'Joe Dassin', 'joe dassin')")
        db.execSQL("INSERT INTO record_artists (recordId, artistId, position) VALUES ('rec-1', 'a-1', 0)")
    }

    private fun insertSeries(id: String, normalizedPublisher: String): String =
        """
        INSERT INTO series (id, collectionType, title, originalAuthor, normalizedTitle,
                            normalizedAuthor, normalizedPublisher, normalizedEdition,
                            createdAt, updatedAt, collectionId, syncState)
        VALUES ('$id', 'MANGA', 'L''attaque des titans', 'Hajime Isayama',
                '${normalizeSeriesKey("L'attaque des titans").replace("'", "''")}',
                'hajime isayama', '$normalizedPublisher', '', 1, 1, '', 'LOCAL_ONLY')
        """.trimIndent()

    private fun String.withTable(table: String): String = replace("\${TABLE_NAME}", table)

    private companion object {
        const val DB_NAME = "migration-7-8-test.db"

        /** Chemin relatif au module : c'est le répertoire de travail des tests JVM Gradle. */
        const val SCHEMA_V7 = "schemas/app.acervatim.data.local.db.AcervatimDatabase/7.json"
    }
}
