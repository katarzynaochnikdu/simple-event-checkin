package pl.medidesk.mobile.feature.scanner.presentation.screen

import android.Manifest
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import pl.medidesk.mobile.core.ui.theme.ScanDuplicate
import pl.medidesk.mobile.core.ui.theme.ScanError
import pl.medidesk.mobile.core.ui.theme.ScanSuccess
import pl.medidesk.mobile.feature.scanner.presentation.viewmodel.ScanFeedback
import pl.medidesk.mobile.feature.scanner.presentation.viewmodel.ScannerUiState
import pl.medidesk.mobile.feature.scanner.presentation.viewmodel.ScannerViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    eventId: String,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermission.status.isGranted) {
            CameraPreview(
                eventId = eventId,
                isScanning = uiState.isScanning,
                onQrDetected = { ticketId -> viewModel.onQrScanned(ticketId, eventId) }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Wymagane uprawnienie do kamery")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text("Przyznaj uprawnienie")
                    }
                }
            }
        }

        // Scan result overlay
        ScanResultOverlay(uiState = uiState)

        // Sync status badge
        if (uiState.syncState.totalPending > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "${uiState.syncState.totalPending} oczekujących",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ScanResultOverlay(uiState: ScannerUiState) {
    AnimatedVisibility(
        visible = uiState.feedback != ScanFeedback.NONE && uiState.feedback != ScanFeedback.PROCESSING,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val (bgColor, statusText) = when (uiState.feedback) {
            ScanFeedback.SUCCESS -> ScanSuccess.copy(alpha = 0.92f) to "WEJŚCIE OK"
            ScanFeedback.SUCCESS_OFFLINE -> ScanDuplicate.copy(alpha = 0.92f) to "ZAPISANO OFFLINE"
            ScanFeedback.DUPLICATE -> ScanDuplicate.copy(alpha = 0.92f) to "JUŻ ZAREJESTROWANY"
            ScanFeedback.NOT_FOUND -> ScanError.copy(alpha = 0.92f) to "NIE ZNALEZIONO"
            ScanFeedback.ERROR -> ScanError.copy(alpha = 0.92f) to "BŁĄD"
            else -> Color.Transparent to ""
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                uiState.lastResult?.participant?.let { p ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = p.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = p.ticketName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                    if (p.company.isNotBlank()) {
                        Text(
                            text = p.company,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    eventId: String,
    isScanning: Boolean,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    if (!isScanning) { imageProxy.close(); return@setAnalyzer }
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull()?.rawValue?.let { ticketId ->
                                    onQrDetected(ticketId)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
