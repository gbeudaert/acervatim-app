package fr.beudaert.acervus.data.backup

import fr.beudaert.acervus.domain.model.Record
import fr.beudaert.acervus.domain.model.Series
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pack / unpack d'un backup `.zip` contenant :
 *  - `manifest.json` — payload [BackupSchemaV1]
 *  - `covers/<id>.jpg` — un fichier par cover locale référencée
 *
 * Le code n'écrit/lit que des streams pour ne pas dépendre d'Android dans les usecases.
 * La résolution des fichiers covers (lecture / écriture sur disque) est laissée à l'appelant.
 */
object BackupZip {

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val COVERS_PREFIX = "covers/"

    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun write(
        output: OutputStream,
        records: List<Record>,
        series: List<Series>,
        coverFileResolver: (path: String) -> File?,
        exportedAt: Instant,
        appVersionName: String?
    ) {
        ZipOutputStream(output).use { zip ->
            val recordCoverEntries: Map<String, File> = records
                .mapNotNull { rec -> rec.coverPath?.let { resolved -> rec.id to resolved } }
                .mapNotNull { (id, path) ->
                    coverFileResolver(path)?.takeIf { it.exists() && it.isFile }?.let { id to it }
                }
                .toMap()

            val seriesCoverEntries: Map<String, File> = series
                .mapNotNull { s -> s.coverPath?.let { resolved -> s.id to resolved } }
                .mapNotNull { (id, path) ->
                    coverFileResolver(path)?.takeIf { it.exists() && it.isFile }?.let { id to it }
                }
                .toMap()

            val recordDtos = records.map { record ->
                val entryName = recordCoverEntries[record.id]?.let { "${COVERS_PREFIX}r-${record.id}.jpg" }
                record.toBackupDto(coverInZip = entryName)
            }
            val seriesDtos = series.map { s ->
                val entryName = seriesCoverEntries[s.id]?.let { "${COVERS_PREFIX}s-${s.id}.jpg" }
                s.toBackupDto(coverInZip = entryName)
            }

            val schema = BackupSchemaV1(
                exportedAt = exportedAt,
                appVersionName = appVersionName,
                records = recordDtos,
                series = seriesDtos
            )

            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(schema).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            recordCoverEntries.forEach { (id, file) ->
                writeCover(zip, "${COVERS_PREFIX}r-$id.jpg", file)
            }
            seriesCoverEntries.forEach { (id, file) ->
                writeCover(zip, "${COVERS_PREFIX}s-$id.jpg", file)
            }
        }
    }

    /**
     * Lit le zip et renvoie le schéma + les bytes de chaque cover indexés par leur
     * chemin relatif dans le zip (clé identique à `coverInZip` des DTOs).
     */
    fun read(input: InputStream): BackupPayload {
        var schema: BackupSchemaV1? = null
        val covers = mutableMapOf<String, ByteArray>()

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == MANIFEST_ENTRY -> {
                        schema = json.decodeFromString(BackupSchemaV1.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                    }
                    entry.name.startsWith(COVERS_PREFIX) && !entry.isDirectory -> {
                        covers[entry.name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val resolved = schema ?: throw IOException("Backup invalide : manifest.json manquant")
        return BackupPayload(schema = resolved, coverBytesByZipPath = covers)
    }

    private fun writeCover(zip: ZipOutputStream, entryName: String, file: File) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}

data class BackupPayload(
    val schema: BackupSchemaV1,
    val coverBytesByZipPath: Map<String, ByteArray>
)
