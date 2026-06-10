package pl.medidesk.mobile.core.ui.components

// 🛑 WO-MOB-034 (F2A-012) — MARTWY KOD (zero konsumentów w app/features, zweryfikowane
// greppem 2026-06-10). Pozostałość po wyłączonych modułach feature-* (image upload/crop).
// Para z dead `MobileApiService.uploadImage` (core-network, też bez wywołań) +
// `ImageCropScreen` (zapisuje PNG do cacheDir/crop_results bez cleanupu).
// Zachowane świadomie (wariant „komentarz-strażnik", mniej inwazyjny niż przeniesienie).
// RE-USE WYMAGA security review: upload obrazów = wektor F2C-001 (sniff MIME, SVG),
// a write na cacheDir bez retencji = higiena at-rest. NIE podłączać bez audytu.

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

/**
 * Manages the image picker + crop flow.
 *
 * Usage:
 * ```kotlin
 * val imagePicker = rememberImagePickerCropperState(
 *     presets = listOf(
 *         CropPreset("Banner 16:9", aspectRatio = 16f / 9f, shape = CropShape.RECTANGLE, outputWidth = 1280, outputHeight = 720),
 *         CropPreset("Avatar", aspectRatio = 1f, shape = CropShape.CIRCLE, outputWidth = 256, outputHeight = 256),
 *     ),
 *     onResult = { results -> /* handle crop results */ },
 * )
 *
 * // In your UI:
 * Button(onClick = { imagePicker.launch() }) { Text("Wybierz zdjęcie") }
 *
 * // Also compose the crop overlay when active:
 * imagePicker.CropOverlay()
 * ```
 */
class ImagePickerCropperState(
    internal val presets: List<CropPreset>,
    private val onResult: (List<CropResult>) -> Unit,
) {
    var selectedUri: Uri? by mutableStateOf(null)
        private set

    var isCropping: Boolean by mutableStateOf(false)
        private set

    internal var launchPicker: (() -> Unit)? = null

    fun launch() {
        launchPicker?.invoke()
    }

    internal fun onImagePicked(uri: Uri?) {
        if (uri != null) {
            selectedUri = uri
            isCropping = true
        }
    }

    internal fun onCropComplete(results: List<CropResult>) {
        isCropping = false
        onResult(results)
    }

    internal fun onCropCancel() {
        isCropping = false
        selectedUri = null
    }
}

/**
 * Remember and set up the image picker + crop state.
 *
 * @param presets Crop presets for the multi-step crop flow
 * @param onResult Callback with all crop results when crop flow is complete
 */
@Composable
fun rememberImagePickerCropperState(
    presets: List<CropPreset>,
    onResult: (List<CropResult>) -> Unit,
): ImagePickerCropperState {
    val state = remember { ImagePickerCropperState(presets, onResult) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        state.onImagePicked(uri)
    }

    state.launchPicker = {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    return state
}

/**
 * Composable that renders the crop screen when active.
 * Place this at the top level of your screen (e.g. inside a Box { ... }).
 */
@Composable
fun ImagePickerCropperState.CropOverlay() {
    val uri = selectedUri
    if (isCropping && uri != null) {
        ImageCropScreen(
            imageUri = uri,
            presets = presets,
            onComplete = { results -> onCropComplete(results) },
            onCancel = { onCropCancel() },
        )
    }
}
