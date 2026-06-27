package app.acervatim.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acervatim.R
import kotlinx.coroutines.delay
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    onBack: () -> Unit,
    onExistingRecord: (recordId: String) -> Unit,
    onNewBarcode: (barcode: String) -> Unit,
    viewModel: BarcodeScanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onCameraPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.onCameraPermissionResult(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val onExisting by rememberUpdatedState(onExistingRecord)
    val onNew by rememberUpdatedState(onNewBarcode)

    LaunchedEffect(event) {
        when (val e = event) {
            is BarcodeScanEvent.OpenExistingRecord -> {
                delay(FEEDBACK_DELAY_MS)
                viewModel.onEventConsumed()
                onExisting(e.recordId)
            }
            is BarcodeScanEvent.ProposeNewRecord -> {
                delay(FEEDBACK_DELAY_MS)
                viewModel.onEventConsumed()
                onNew(e.barcode)
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            when {
                !state.permissionResolved -> PermissionLoading()
                !state.permissionGranted -> PermissionDeniedState(
                    onRetry = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onBack = onBack
                )
                else -> CameraScanContent(
                    onBarcodeDetected = viewModel::onBarcodeDetected,
                    detection = state.detection
                )
            }
        }
    }
}

@Composable
private fun PermissionLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun PermissionDeniedState(onRetry: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.scan_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.scan_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.scan_permission_retry))
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@Composable
private fun CameraScanContent(
    onBarcodeDetected: (String) -> Unit,
    detection: DetectionStep
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            onBarcodeDetected = onBarcodeDetected,
            paused = detection !is DetectionStep.Idle,
            modifier = Modifier.fillMaxSize()
        )
        ViewfinderOverlay(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            DetectionPanel(detection)
        }
    }
}

@Composable
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onBarcodeDetected)

    val analyzer = remember { BarcodeAnalyzer { value -> callback(value) } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(paused) {
        if (paused) analyzer.pause() else analyzer.resume()
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.release()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            bindCamera(
                context = ctx,
                previewView = previewView,
                lifecycleOwner = lifecycleOwner,
                analyzer = analyzer,
                executor = executor
            )
            previewView
        }
    )
}

private fun bindCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    analyzer: BarcodeAnalyzer,
    executor: Executor
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val cameraProvider = providerFuture.get() ?: return@addListener
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(executor, analyzer) }
        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        }
    }, ContextCompat.getMainExecutor(context))
}

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val frameWidth = w * 0.78f
        val frameHeight = frameWidth * 0.5f
        val left = (w - frameWidth) / 2f
        val top = (h - frameHeight) / 2f
        val scrim = Color.Black.copy(alpha = 0.55f)

        // Voile en 4 bandes autour de la fenêtre de visée — le centre reste vierge pour
        // laisser la caméra transparaître (pas de BlendMode.Clear : il rendrait du noir
        // sans calque de compositing dédié).
        drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(w, top))
        drawRect(scrim, topLeft = Offset(0f, top + frameHeight), size = Size(w, h - top - frameHeight))
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(scrim, topLeft = Offset(left + frameWidth, top), size = Size(w - left - frameWidth, frameHeight))

        drawRect(
            color = outline,
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            style = Stroke(width = 4f)
        )
    }
}

@Composable
private fun DetectionPanel(detection: DetectionStep) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (detection) {
                DetectionStep.Idle -> Text(
                    text = stringResource(R.string.scan_hint_align),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                is DetectionStep.Looking -> ProgressRow(
                    text = stringResource(R.string.scan_status_lookup, detection.barcode)
                )
                is DetectionStep.Enriching -> ProgressRow(
                    text = stringResource(R.string.scan_status_enriching, detection.barcode)
                )
                is DetectionStep.Enriched -> Text(
                    text = stringResource(R.string.scan_status_enriched, detection.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                is DetectionStep.Found -> Text(
                    text = stringResource(R.string.scan_status_already_present, detection.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                is DetectionStep.NotInCollection -> Text(
                    text = stringResource(R.string.scan_status_new_record, detection.barcode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ProgressRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

private const val FEEDBACK_DELAY_MS = 700L
