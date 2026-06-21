package app.acervatim.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fine abstraction sur le dossier `filesDir/covers/` utilisée par l'export / import
 * de backup. Volontairement minimal : pas de resize ni d'EXIF (à l'inverse de
 * `CoverStorage` qui transforme l'image à l'import utilisateur). Ici on copie les
 * bytes verbatim — un backup doit produire un round-trip pixel-pour-pixel.
 */
interface BackupCoverIo {
    /** Renvoie le fichier cover existant à ce chemin absolu, null s'il n'existe pas. */
    fun resolveExistingCover(absolutePath: String): File?

    /** Destination canonique d'une cover de disque (créée si nécessaire, vide). */
    fun coverFileForRecord(recordId: String): File

    /** Destination canonique d'une cover de série (créée si nécessaire, vide). */
    fun coverFileForSeries(seriesId: String): File

    /** Vide complètement le dossier covers. Utilisé par l'import avant restauration. */
    fun deleteAllCovers()
}

@Singleton
class BackupCoverIoImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BackupCoverIo {

    override fun resolveExistingCover(absolutePath: String): File? =
        File(absolutePath).takeIf { it.exists() && it.isFile }

    override fun coverFileForRecord(recordId: String): File {
        requireValidBackupId(recordId, "record")
        return resolveSafeChild("$recordId.jpg")
    }

    override fun coverFileForSeries(seriesId: String): File {
        requireValidBackupId(seriesId, "series")
        return resolveSafeChild("series-$seriesId.jpg")
    }

    override fun deleteAllCovers() {
        val dir = coversDir()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun coversDir(): File =
        File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }

    // Defense-in-depth : même si la validation regex a déjà eu lieu en amont, on refuse
    // tout chemin canonique qui ne reste pas strictement sous le dossier covers.
    private fun resolveSafeChild(fileName: String): File {
        val dir = coversDir()
        val target = File(dir, fileName)
        val dirPath = dir.canonicalPath + File.separator
        check(target.canonicalPath.startsWith(dirPath)) {
            "Chemin cover hors du dossier covers détecté : ${target.canonicalPath}"
        }
        return target
    }

    private companion object {
        const val COVERS_DIR = "covers"
    }
}

/**
 * Caractères autorisés pour un identifiant de backup (record ou series). Suffisamment
 * large pour couvrir UUID / slug ASCII, mais refuse `.`, `/`, `\`, espaces, etc.
 * → bloque toute tentative de path traversal via le champ `id` d'un JSON importé.
 */
internal val BACKUP_ID_REGEX: Regex = Regex("^[A-Za-z0-9_-]{1,64}$")

internal fun requireValidBackupId(id: String, kind: String) {
    require(BACKUP_ID_REGEX.matches(id)) {
        "Identifiant $kind invalide dans le backup : '$id'"
    }
}
