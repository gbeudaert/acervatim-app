package app.acervatim.ui.scan

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128
            )
            .build()
    )

    private val paused = AtomicBoolean(false)

    // Une lecture isolée (flou de bougé, reflet) peut décoder une valeur erronée sur une
    // seule frame : on n'accepte un code que lu identiquement sur plusieurs frames de suite,
    // et une seule fois par session de scan (jusqu'au prochain resume()).
    private var lastValue: String? = null
    private var consecutiveCount = 0
    private var confirmed = false

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
        lastValue = null
        consecutiveCount = 0
        confirmed = false
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (paused.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                onCandidate(value)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onCandidate(value: String?) {
        if (confirmed) return
        if (value == null || value != lastValue) {
            lastValue = value
            consecutiveCount = if (value == null) 0 else 1
        } else {
            consecutiveCount++
        }
        if (consecutiveCount >= CONFIRMATION_THRESHOLD) {
            confirmed = true
            onBarcodeDetected(value!!)
        }
    }

    fun release() {
        scanner.close()
    }

    private companion object {
        const val CONFIRMATION_THRESHOLD = 2
    }
}
