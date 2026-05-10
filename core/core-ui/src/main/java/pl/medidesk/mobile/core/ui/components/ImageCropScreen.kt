package pl.medidesk.mobile.core.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Shape of the crop mask overlay.
 */
enum class CropShape {
    RECTANGLE,
    CIRCLE
}

/**
 * Configuration for a single crop step in the multi-step crop flow.
 *
 * @param label User-visible label for this step (e.g. "Banner 16:9", "Avatar")
 * @param aspectRatio Width/height ratio (null = free aspect ratio)
 * @param shape Shape of the overlay mask
 * @param outputWidth Desired output width in pixels
 * @param outputHeight Desired output height in pixels
 */
data class CropPreset(
    val label: String,
    val aspectRatio: Float? = null,
    val shape: CropShape = CropShape.RECTANGLE,
    val outputWidth: Int = 512,
    val outputHeight: Int = 512,
)

/**
 * Result of a single crop step.
 */
data class CropResult(
    val preset: CropPreset,
    val file: File,
    val bitmap: Bitmap,
)

/**
 * Full-screen image crop composable with pinch-to-zoom, pan, and shape overlay.
 *
 * Supports multi-step cropping — presets are processed from largest to smallest.
 * Each step shows the original image with a different crop mask.
 *
 * @param imageUri URI of the image to crop (content:// or file://)
 * @param presets List of crop configurations to apply sequentially
 * @param onComplete Callback with all crop results when user finishes all steps
 * @param onCancel Callback when user cancels the crop flow
 */
@Composable
fun ImageCropScreen(
    imageUri: Uri,
    presets: List<CropPreset>,
    onComplete: (List<CropResult>) -> Unit,
    onCancel: () -> Unit,
) {
    require(presets.isNotEmpty()) { "At least one CropPreset is required" }

    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentStep by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<CropResult>() }

    // Image transform state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Load bitmap on first composition
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            sourceBitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
        }
    }

    // Reset transform on step change
    LaunchedEffect(currentStep) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val bitmap = sourceBitmap
    if (bitmap == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val currentPreset = presets[currentStep]
    val isLastStep = currentStep == presets.lastIndex
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Anuluj", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    currentPreset.label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    "Krok ${currentStep + 1} z ${presets.size}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            // Empty spacer for symmetry
            Spacer(Modifier.size(48.dp))
        }

        // Crop canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it }
                .pointerInput(currentStep) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val imageBitmap = bitmap.asImageBitmap()
            val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // Fit image to canvas
                val fitScale: Float
                val imgDrawW: Float
                val imgDrawH: Float
                if (imageAspect > canvasW / canvasH) {
                    fitScale = canvasW / bitmap.width
                    imgDrawW = canvasW
                    imgDrawH = bitmap.height * fitScale
                } else {
                    fitScale = canvasH / bitmap.height
                    imgDrawW = bitmap.width * fitScale
                    imgDrawH = canvasH
                }

                val totalScale = fitScale * scale
                val imgLeft = (canvasW - imgDrawW * scale) / 2f + offsetX
                val imgTop = (canvasH - imgDrawH * scale) / 2f + offsetY

                // Draw image
                drawImage(
                    image = imageBitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(imgLeft.toInt(), imgTop.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(
                        (imgDrawW * scale).toInt(),
                        (imgDrawH * scale).toInt()
                    ),
                )

                // Draw overlay
                drawCropOverlay(
                    canvasWidth = canvasW,
                    canvasHeight = canvasH,
                    cropShape = currentPreset.shape,
                    aspectRatio = currentPreset.aspectRatio,
                )
            }
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = {
                        results.removeLastOrNull()
                        currentStep--
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Wstecz")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    // Crop the image
                    val cropRect = computeCropRect(
                        canvasSize = canvasSize,
                        bitmap = bitmap,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        aspectRatio = currentPreset.aspectRatio,
                        cropShape = currentPreset.shape,
                    )
                    val cropped = cropBitmap(bitmap, cropRect, currentPreset)
                    val file = saveBitmapToCache(context, cropped, "crop_${currentStep}")
                    results.add(CropResult(currentPreset, file, cropped))

                    if (isLastStep) {
                        onComplete(results.toList())
                    } else {
                        currentStep++
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
            ) {
                Text(if (isLastStep) "Potwierdź" else "Dalej")
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ─── Canvas overlay drawing ─────────────────────────────────────────

private fun DrawScope.drawCropOverlay(
    canvasWidth: Float,
    canvasHeight: Float,
    cropShape: CropShape,
    aspectRatio: Float?,
) {
    val margin = 32f
    val maxCropW = canvasWidth - margin * 2
    val maxCropH = canvasHeight - margin * 2

    val cropW: Float
    val cropH: Float
    if (aspectRatio != null) {
        if (maxCropW / maxCropH > aspectRatio) {
            cropH = maxCropH
            cropW = cropH * aspectRatio
        } else {
            cropW = maxCropW
            cropH = cropW / aspectRatio
        }
    } else {
        cropW = maxCropW
        cropH = maxCropH
    }

    val cropLeft = (canvasWidth - cropW) / 2f
    val cropTop = (canvasHeight - cropH) / 2f

    val overlayColor = Color.Black.copy(alpha = 0.55f)

    val path = Path().apply {
        addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
        when (cropShape) {
            CropShape.RECTANGLE -> {
                addRect(Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH))
            }
            CropShape.CIRCLE -> {
                val radius = minOf(cropW, cropH) / 2f
                val cx = canvasWidth / 2f
                val cy = canvasHeight / 2f
                addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
            }
        }
    }
    path.fillType = PathFillType.EvenOdd

    drawPath(path, overlayColor)

    // Draw border
    when (cropShape) {
        CropShape.RECTANGLE -> {
            drawRect(
                color = Color.White.copy(alpha = 0.7f),
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropW, cropH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
        CropShape.CIRCLE -> {
            val radius = minOf(cropW, cropH) / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = radius,
                center = Offset(canvasWidth / 2f, canvasHeight / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
    }
}

// ─── Crop math ──────────────────────────────────────────────────────

private fun computeCropRect(
    canvasSize: IntSize,
    bitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    aspectRatio: Float?,
    cropShape: CropShape,
): android.graphics.Rect {
    val canvasW = canvasSize.width.toFloat()
    val canvasH = canvasSize.height.toFloat()
    val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

    val fitScale: Float
    val imgDrawW: Float
    val imgDrawH: Float
    if (imageAspect > canvasW / canvasH) {
        fitScale = canvasW / bitmap.width
        imgDrawW = canvasW
        imgDrawH = bitmap.height * fitScale
    } else {
        fitScale = canvasH / bitmap.height
        imgDrawW = bitmap.width * fitScale
        imgDrawH = canvasH
    }

    val totalScale = fitScale * scale
    val imgLeft = (canvasW - imgDrawW * scale) / 2f + offsetX
    val imgTop = (canvasH - imgDrawH * scale) / 2f + offsetY

    // Compute crop window in canvas coords
    val margin = 32f
    val maxCropW = canvasW - margin * 2
    val maxCropH = canvasH - margin * 2

    val cropW: Float
    val cropH: Float
    if (aspectRatio != null) {
        if (maxCropW / maxCropH > aspectRatio) {
            cropH = maxCropH; cropW = cropH * aspectRatio
        } else {
            cropW = maxCropW; cropH = cropW / aspectRatio
        }
    } else {
        cropW = maxCropW; cropH = maxCropH
    }

    val cropLeft: Float
    val cropTop: Float
    if (cropShape == CropShape.CIRCLE) {
        val radius = minOf(cropW, cropH) / 2f
        cropLeft = canvasW / 2f - radius
        cropTop = canvasH / 2f - radius
    } else {
        cropLeft = (canvasW - cropW) / 2f
        cropTop = (canvasH - cropH) / 2f
    }

    // Map from canvas to bitmap coords
    val bmpX = ((cropLeft - imgLeft) / totalScale).toInt().coerceIn(0, bitmap.width - 1)
    val bmpY = ((cropTop - imgTop) / totalScale).toInt().coerceIn(0, bitmap.height - 1)
    val bmpW = (cropW / totalScale).toInt().coerceIn(1, bitmap.width - bmpX)
    val bmpH = (cropH / totalScale).toInt().coerceIn(1, bitmap.height - bmpY)

    return android.graphics.Rect(bmpX, bmpY, bmpX + bmpW, bmpY + bmpH)
}

private fun cropBitmap(
    source: Bitmap,
    rect: android.graphics.Rect,
    preset: CropPreset,
): Bitmap {
    val cropped = Bitmap.createBitmap(
        source,
        rect.left.coerceIn(0, source.width - 1),
        rect.top.coerceIn(0, source.height - 1),
        rect.width().coerceIn(1, source.width - rect.left.coerceIn(0, source.width - 1)),
        rect.height().coerceIn(1, source.height - rect.top.coerceIn(0, source.height - 1)),
    )
    // Scale to target output size
    return Bitmap.createScaledBitmap(cropped, preset.outputWidth, preset.outputHeight, true)
}

private fun saveBitmapToCache(
    context: android.content.Context,
    bitmap: Bitmap,
    name: String,
): File {
    val dir = File(context.cacheDir, "crop_results").also { it.mkdirs() }
    val file = File(dir, "${name}_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file
}
