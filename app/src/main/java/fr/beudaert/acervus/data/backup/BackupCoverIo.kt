package fr.beudaert.acervus.data.backup

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

    override fun coverFileForRecord(recordId: String): File =
        File(coversDir(), "$recordId.jpg")

    override fun coverFileForSeries(seriesId: String): File =
        File(coversDir(), "series-$seriesId.jpg")

    override fun deleteAllCovers() {
        val dir = coversDir()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun coversDir(): File =
        File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }

    private companion object {
        const val COVERS_DIR = "covers"
    }
}
