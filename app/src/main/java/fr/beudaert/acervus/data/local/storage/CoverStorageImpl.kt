package fr.beudaert.acervus.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.beudaert.acervus.domain.repository.CoverStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CoverStorage {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun pathFor(recordId: String): String =
        File(coversDir(), "$recordId$EXT").absolutePath

    override suspend fun saveFromUri(recordId: String, uriString: String): String =
        withContext(ioDispatcher) {
            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver
            val bitmap = resolver.openInputStream(uri)?.use { decodeDownsampled(it) }
                ?: throw IOException("Cannot open URI: $uri")
            val rotation = resolver.openInputStream(uri)?.use { readExifRotation(it) } ?: 0
            val rotated = applyRotation(bitmap, rotation)
            writeJpeg(recordId, rotated)
        }

    override suspend fun saveFromBytes(recordId: String, bytes: ByteArray): String =
        withContext(ioDispatcher) {
            val bitmap = decodeDownsampled(bytes.inputStream())
                ?: throw IOException("Cannot decode bytes")
            val rotation = readExifRotation(bytes.inputStream())
            val rotated = applyRotation(bitmap, rotation)
            writeJpeg(recordId, rotated)
        }

    override suspend fun saveFromUrl(recordId: String, url: String): String =
        withContext(ioDispatcher) {
            val bytes = URL(url).openStream().use { it.readBytes() }
            saveFromBytes(recordId, bytes)
        }

    override suspend fun delete(recordId: String) {
        withContext(ioDispatcher) {
            File(coversDir(), "$recordId$EXT").takeIf { it.exists() }?.delete()
        }
    }

    private fun coversDir(): File =
        File(context.filesDir, COVERS_DIR).apply { if (!exists()) mkdirs() }

    private fun decodeDownsampled(input: InputStream): Bitmap? {
        val bytes = input.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        val sample = if (longest <= 0) 1 else calculateInSampleSize(longest, MAX_LONG_EDGE_PX)
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val w = decoded.width
        val h = decoded.height
        val biggest = maxOf(w, h)
        return if (biggest <= MAX_LONG_EDGE_PX) {
            decoded
        } else {
            val scale = MAX_LONG_EDGE_PX.toFloat() / biggest
            val targetW = (w * scale).toInt().coerceAtLeast(1)
            val targetH = (h * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
    }

    private fun calculateInSampleSize(longest: Int, target: Int): Int {
        var sample = 1
        var current = longest
        while (current / 2 >= target) {
            sample *= 2
            current /= 2
        }
        return sample
    }

    private fun readExifRotation(input: InputStream): Int = try {
        when (ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: IOException) {
        0
    }

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun writeJpeg(recordId: String, bitmap: Bitmap): String {
        val target = File(coversDir(), "$recordId$EXT")
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        bitmap.recycle()
        return target.absolutePath
    }

    private companion object {
        const val COVERS_DIR = "covers"
        const val EXT = ".jpg"
        const val MAX_LONG_EDGE_PX = 1200
        const val JPEG_QUALITY = 85
    }
}
