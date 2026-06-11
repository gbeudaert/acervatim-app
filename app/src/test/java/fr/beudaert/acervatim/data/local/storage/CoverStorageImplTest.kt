package fr.beudaert.acervatim.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CoverStorageImplTest {

    private lateinit var context: Context
    private lateinit var storage: CoverStorageImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = CoverStorageImpl(context)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "covers").deleteRecursively()
        File(context.cacheDir, "test_input").deleteRecursively()
    }

    @Test
    fun `saveFromBytes writes JPEG to covers dir`() = runTest {
        val bytes = makeJpegBytes(width = 200, height = 200, color = Color.RED)

        val path = storage.saveFromBytes("rec-1", bytes)

        val file = File(path)
        assertTrue("Cover file should exist", file.exists())
        assertEquals("rec-1.jpg", file.name)
        assertEquals(File(context.filesDir, "covers").absolutePath, file.parentFile?.absolutePath)
        assertTrue("File should not be empty", file.length() > 0)
    }

    @Test
    fun `saveFromBytes downsizes images larger than 1200px on the long edge`() = runTest {
        val bytes = makeJpegBytes(width = 4000, height = 2000, color = Color.BLUE)

        val path = storage.saveFromBytes("big", bytes)

        val decoded = android.graphics.BitmapFactory.decodeFile(path)
        assertTrue(
            "Long edge should be <= 1200px, was ${decoded.width}x${decoded.height}",
            maxOf(decoded.width, decoded.height) <= 1200
        )
    }

    @Test
    fun `saveFromUri reads through content resolver and writes JPEG`() = runTest {
        val bytes = makeJpegBytes(width = 300, height = 300, color = Color.GREEN)
        val uri = stageInputFile(bytes)

        val path = storage.saveFromUri("rec-2", uri.toString())

        assertTrue(File(path).exists())
    }

    @Test
    fun `pathFor returns target without writing`() {
        val path = storage.pathFor("rec-3")
        assertEquals(File(context.filesDir, "covers/rec-3.jpg").absolutePath, path)
        assertFalse(File(path).exists())
    }

    @Test
    fun `delete removes the file when present`() = runTest {
        val bytes = makeJpegBytes(width = 100, height = 100, color = Color.GRAY)
        val path = storage.saveFromBytes("rec-4", bytes)
        assertTrue(File(path).exists())

        storage.delete("rec-4")

        assertFalse(File(path).exists())
    }

    @Test
    fun `delete on missing file is a noop`() = runTest {
        storage.delete("does-not-exist")
        // no exception is the assertion
    }

    private fun makeJpegBytes(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /**
     * Stage les octets derrière une URI `content://` lue via le `ContentResolver`. On passe par
     * [org.robolectric.Shadows.shadowOf] car Robolectric n'ouvre pas un `file://` à travers le
     * resolver (FileNotFoundException). Reflète l'usage réel (image choisie = content:// du
     * sélecteur système).
     */
    private fun stageInputFile(bytes: ByteArray): Uri {
        val uri = Uri.parse("content://acervatim.test/cover-${System.nanoTime()}")
        val resolver = context.contentResolver
        org.robolectric.Shadows.shadowOf(resolver)
            .registerInputStream(uri, java.io.ByteArrayInputStream(bytes))
        return uri
    }
}
