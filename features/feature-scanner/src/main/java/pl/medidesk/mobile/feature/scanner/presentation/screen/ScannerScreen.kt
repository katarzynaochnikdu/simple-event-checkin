package pl.medidesk.mobile.feature.scanner.presentation.screen

import android.Manifest
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import pl.medidesk.mobile.feature.scanner.presentation.viewmodel.PendingScan
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

        ScanResultOverlay(uiState = uiState, onUndo = { viewModel.undoLastScan() })

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

    uiState.pendingScan?.let { pending ->
        ScanConfirmDialog(
            pending = pending,
            onConfirm = { viewModel.confirmScan() },
            onDismiss = { viewModel.cancelScan() }
        )
    }
}

@Composable
private fun ScanConfirmDialog(
    pending: PendingScan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.MeetingRoom,
                contentDescription = null,
                tint = ScanSuccess,
                modifier = Modifier.size(36.dp)
            )
        },
        title = { Text("Potwierdzenie Check-In", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (pending.participantName.isNotBlank()) {
                    Text("Zweryfikuj dane osoby przed zatwierdzeniem:")
                    Spacer(Modifier.height(12.dp))
                    Text(pending.participantName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (pending.email.isNotBlank()) {
                        Text(pending.email, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (pending.ticketName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Bilet: ${pending.ticketName}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (pending.company.isNotBlank()) {
                        Text("Firma: ${pending.company}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (pending.alreadyCheckedIn) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Uwaga: ten uczestnik jest już oznaczony jako obecny.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val unpaid = pending.orderStatus != null
                            && pending.orderStatus !in listOf("paid", "free")
                    if (unpaid) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "ZAMÓWIENIE NIEOPŁACONE",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    Text("Nie udało się pobrać danych biletu (brak w lokalnej bazie i sieci).")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Anuluj i spróbuj ponownie po synchronizacji, albo zatwierdź na własną odpowiedzialność — backend i tak zweryfikuje bilet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ScanSuccess)
            ) {
                Text("Tak, Check-In", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
private fun ScanResultOverlay(uiState: ScannerUiState, onUndo: () -> Unit) {
    val showOverlay = uiState.feedback != ScanFeedback.NONE && uiState.feedback != ScanFeedback.PROCESSING
    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val (bgColor, statusText) = when (uiState.feedback) {
            ScanFeedback.SUCCESS -> ScanSuccess.copy(alpha = 0.95f) to "WEJŚCIE OK"
            ScanFeedback.SUCCESS_OFFLINE -> ScanDuplicate.copy(alpha = 0.92f) to "ZAPISANO OFFLINE"
            ScanFeedback.DUPLICATE -> ScanDuplicate.copy(alpha = 0.92f) to "JUŻ ZAREJESTROWANY"
            ScanFeedback.NOT_FOUND -> ScanError.copy(alpha = 0.92f) to "NIE ZNALEZIONO"
            ScanFeedback.WRONG_EVENT -> ScanError.copy(alpha = 0.95f) to "BILET Z INNEGO WYDARZENIA"
            ScanFeedback.ERROR -> ScanError.copy(alpha = 0.92f) to "BŁĄD"
            ScanFeedback.UNDOING -> ScanDuplicate.copy(alpha = 0.92f) to "COFANIE..."
            ScanFeedback.UNDONE -> ScanSuccess.copy(alpha = 0.92f) to "COFNIĘTO"
            ScanFeedback.DENIED -> ScanError.copy(alpha = 0.95f) to "Niepotwierdzone wejście"
            else -> Color.Transparent to ""
        }

        val showUndoButton = uiState.feedback == ScanFeedback.SUCCESS || uiState.feedback == ScanFeedback.SUCCESS_OFFLINE
        val showCheckmarkAnim = uiState.feedback == ScanFeedback.SUCCESS || uiState.feedback == ScanFeedback.UNDONE
        val showCrossmarkAnim = uiState.feedback == ScanFeedback.DENIED

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showCheckmarkAnim) {
                    AnimatedCheckmark()
                    Spacer(Modifier.height(16.dp))
                }
                if (showCrossmarkAnim) {
                    AnimatedCrossmark()
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                uiState.wrongEventInfo?.let { info ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = info.participantName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Ten bilet przypisany jest do innego wydarzenia.\nZeskanuj bilet z aktualnego eventu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
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
                if (uiState.feedback == ScanFeedback.DENIED) {
                    uiState.pendingScan?.let { ps ->
                        if (ps.participantName.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = ps.participantName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            if (ps.ticketName.isNotBlank()) {
                                Text(
                                    text = ps.ticketName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (ps.company.isNotBlank()) {
                                Text(
                                    text = ps.company,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                if (showUndoButton) {
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = onUndo,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                    ) {
                        Text("Cofnij wejście", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/**
 * Animowany "fajny" checkmark — koło wjeżdża spring'iem, potem po jego krawędzi
 * rysuje się gruba linia checkmarka. Czas trwania ~700 ms łącznie.
 */
@Composable
private fun AnimatedCheckmark() {
    val circleScale = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        circleScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        checkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 380, easing = LinearEasing)
        )
    }

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(circleScale.value)
            .background(Color.White.copy(alpha = 0.18f), shape = androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = w * 0.40f

            // Białe koło wypełnienie
            drawCircle(color = Color.White, radius = r, center = Offset(cx, cy))

            // Punkty checkmarka — względem środka koła
            val p1 = Offset(cx - r * 0.45f, cy + r * 0.05f)
            val p2 = Offset(cx - r * 0.10f, cy + r * 0.40f)
            val p3 = Offset(cx + r * 0.50f, cy - r * 0.30f)

            // Animowane rysowanie checkmarka (dwa segmenty: p1→p2, p2→p3)
            val seg1Len = distance(p1, p2)
            val seg2Len = distance(p2, p3)
            val totalLen = seg1Len + seg2Len
            val drawnLen = totalLen * checkProgress.value

            val path = Path().apply { moveTo(p1.x, p1.y) }
            if (drawnLen <= seg1Len) {
                val t = if (seg1Len > 0f) drawnLen / seg1Len else 0f
                val end = lerp(p1, p2, t)
                path.lineTo(end.x, end.y)
            } else {
                path.lineTo(p2.x, p2.y)
                val t2 = if (seg2Len > 0f) (drawnLen - seg1Len) / seg2Len else 0f
                val end = lerp(p2, p3, t2)
                path.lineTo(end.x, end.y)
            }

            drawPath(
                path = path,
                color = ScanSuccess,
                style = Stroke(width = r * 0.22f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Animowany czerwony X — koło wjeżdża spring'iem, potem rysują się dwa ramiona krzyżyka.
 * Czas trwania ~700 ms łącznie.
 */
@Composable
private fun AnimatedCrossmark() {
    val circleScale = remember { Animatable(0f) }
    val arm1Progress = remember { Animatable(0f) }
    val arm2Progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        circleScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        arm1Progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = LinearEasing)
        )
        arm2Progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = LinearEasing)
        )
    }

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(circleScale.value)
            .background(Color.White.copy(alpha = 0.18f), shape = androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = w * 0.40f

            // Białe koło wypełnienie
            drawCircle(color = Color.White, radius = r, center = Offset(cx, cy))

            val strokeW = r * 0.22f
            val arm = r * 0.42f

            // Pierwsze ramię X: góra-lewo → dół-prawo
            val a1Start = Offset(cx - arm, cy - arm)
            val a1End   = Offset(cx + arm, cy + arm)
            val arm1End = lerp(a1Start, a1End, arm1Progress.value)
            val path1 = Path().apply { moveTo(a1Start.x, a1Start.y); lineTo(arm1End.x, arm1End.y) }
            drawPath(path1, color = ScanError, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Drugie ramię X: góra-prawo → dół-lewo
            val a2Start = Offset(cx + arm, cy - arm)
            val a2End   = Offset(cx - arm, cy + arm)
            val arm2End = lerp(a2Start, a2End, arm2Progress.value)
            val path2 = Path().apply { moveTo(a2Start.x, a2Start.y); lineTo(arm2End.x, arm2End.y) }
            drawPath(path2, color = ScanError, style = Stroke(width = strokeW, cap = StrokeCap.Round))
        }
    }
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun lerp(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

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
                                    // WO-204: client-side guard — reject blank or oversized values
                                    // before passing to ViewModel/API. Backstage ticket IDs are
                                    // alphanumeric strings, never longer than ~100 chars.
                                    if (ticketId.isNotBlank() && ticketId.length <= 200) {
                                        onQrDetected(ticketId)
                                    }
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
