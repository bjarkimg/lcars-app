package io.starfleet.lcars.app.scan

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPane(
    enabled: Boolean,
    zoom: Float,
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val onBarcodeState = androidx.compose.runtime.rememberUpdatedState(onBarcode)
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(enabled, lifecycleOwner) {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val analyzer = BarcodeAnalyzer { onBarcodeState.value(it) }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = providerFuture.get()
            cameraProvider.unbindAll()
            camera = null
            if (!enabled) return@Runnable
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }
            val bound = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            camera = bound
            applyZoom(bound, zoom)
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            camera = null
            runCatching { providerFuture.get().unbindAll() }
            analyzer.close()
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(zoom, camera) {
        applyZoom(camera ?: return@LaunchedEffect, zoom)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun applyZoom(camera: Camera, requested: Float) {
    val state = camera.cameraInfo.zoomState.value ?: return
    val ratio = requested.coerceIn(state.minZoomRatio, state.maxZoomRatio)
    camera.cameraControl.setZoomRatio(ratio)
}
