package app.acervatim.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.acervatim.domain.model.normalizeArtistName
import java.util.UUID

/**
 * v1 → v2 : normalisation des artistes.
 *
 * - Nouvelle table `artists(id, name, normalized_name UNIQUE)`.
 * - Nouvelle table de jointure `record_artists(recordId, artistId, position)` (n-n).
 * - Nouvelle colonne `records.recordingSpeed`.
 * - Backfill : pour chaque disque existant, crée (ou réutilise) un artiste
 *   à partir de l'ancienne colonne `records.artist` et insère une ligne
 *   `record_artists(position=0)`.
 * - Suppression de la colonne `records.artist` via recréation de la table
 *   (SQLite < 3.35 ne supporte pas `DROP COLUMN`).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Ajout de la colonne recordingSpeed sur records (avant la recréation,
        //    pour simplifier le SELECT de backfill).
        db.execSQL("ALTER TABLE records ADD COLUMN recordingSpeed TEXT")

        // 2. Création des nouvelles tables.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS artists (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                normalized_name TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_artists_normalized_name ON artists(normalized_name)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS record_artists (
                recordId TEXT NOT NULL,
                artistId TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY (recordId, artistId),
                FOREIGN KEY (recordId) REFERENCES records(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY (artistId) REFERENCES artists(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_record_artists_recordId ON record_artists(recordId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_record_artists_artistId ON record_artists(artistId)")

        // 3. Backfill : un artiste par disque existant (position=0).
        val cursor = db.query("SELECT id, artist FROM records")
        val pairs = mutableListOf<Pair<String, String>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val recordId = c.getString(0)
                val rawArtist = if (c.isNull(1)) "" else c.getString(1)
                if (rawArtist.isNotBlank()) {
                    pairs += recordId to rawArtist
                }
            }
        }
        for ((recordId, name) in pairs) {
            val normalized = normalizeArtistName(name)
            val existing = db.query(
                "SELECT id FROM artists WHERE normalized_name = ? LIMIT 1",
                arrayOf<Any?>(normalized)
            )
            val artistId = existing.use { c ->
                if (c.moveToNext()) c.getString(0) else null
            } ?: run {
                val newId = UUID.randomUUID().toString()
                db.execSQL(
                    "INSERT INTO artists (id, name, normalized_name) VALUES (?, ?, ?)",
                    arrayOf<Any?>(newId, name.trim(), normalized)
                )
                newId
            }
            db.execSQL(
                "INSERT OR IGNORE INTO record_artists (recordId, artistId, position) VALUES (?, ?, 0)",
                arrayOf<Any?>(recordId, artistId)
            )
        }

        // 4. Recréation de `records` sans la colonne `artist`.
        db.execSQL(
            """
            CREATE TABLE records_new (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                genre TEXT,
                year INTEGER,
                label TEXT,
                format TEXT,
                recordingSpeed TEXT,
                country TEXT,
                purchasePrice REAL,
                rating INTEGER,
                coverPath TEXT,
                coverRemoteUrl TEXT,
                barcode TEXT,
                discogsId INTEGER,
                status TEXT NOT NULL,
                lastPlayedAt INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO records_new
                SELECT id, title, genre, year, label, format, recordingSpeed, country,
                       purchasePrice, rating, coverPath, coverRemoteUrl, barcode, discogsId,
                       status, lastPlayedAt, createdAt, updatedAt
                FROM records
            """.trimIndent()
        )
        db.execSQL("DROP TABLE records")
        db.execSQL("ALTER TABLE records_new RENAME TO records")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_title ON records(title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_records_status ON records(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_records_barcode ON records(barcode)")
    }
}

/**
 * v6 → v7 : compteur d'écoutes (sprint AD3).
 *
 * Ajoute `records.playCount`, entier non nul par défaut 0. Les disques existants repartent
 * donc de 0 : l'app n'a jamais compté jusqu'ici, il n'y a rien à backfiller (`lastPlayedAt`
 * ne dit pas *combien de fois*). C'est assumé et documenté côté sprint.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE records ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
    }
}
