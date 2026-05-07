package pl.medidesk.mobile.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Reusable async image composable with built-in placeholder and error states.
 *
 * Replaces raw AsyncImage calls throughout the app to provide consistent
 * loading/error UX and eliminate external placehold.co fallbacks.
 *
 * @param model Image URL or any Coil-compatible model
 * @param contentDescription Accessibility description
 * @param modifier Layout modifier
 * @param contentScale How to scale the image within bounds
 * @param initials Fallback text (e.g. first letter of name) shown when no image
 * @param shape Optional clip shape (e.g. CircleShape for avatars)
 * @param placeholderColor Background color for placeholder/error states
 */
@Composable
fun MdAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    initials: String = "",
    shape: Shape? = null,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val clippedModifier = if (shape != null) modifier.clip(shape) else modifier

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = clippedModifier,
        contentScale = contentScale,
        loading = {
            InitialsPlaceholder(
                initials = initials,
                backgroundColor = placeholderColor,
                icon = Icons.Default.Image,
            )
        },
        error = {
            InitialsPlaceholder(
                initials = initials,
                backgroundColor = placeholderColor,
                icon = Icons.Default.BrokenImage,
            )
        },
    )
}

@Composable
private fun InitialsPlaceholder(
    initials: String,
    backgroundColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isNotBlank()) {
            Text(
                text = initials.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
