package pl.medidesk.mobile.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.medidesk.mobile.core.network.dto.ParticipantTagDefinitionDto

/**
 * WO-MOB-016: chip renderowany w spojnych kolorach z desktop ParticipantTagBadge.
 *
 * Input: raw klucz taga (`przedstawiciel_partnera`) + opcjonalna definicja
 * z ParticipantTagsRepository. Gdy `definition` is null - chip uzywa
 * humanizowanego fallback labelu (`Przedstawiciel partnera`) i szarych kolorow.
 *
 * Mapping Tailwind class -> Compose Color zywie tu, w core-ui, bo core-sync
 * jest data-only (zero Compose imports).
 */
@Composable
fun ParticipantTagChip(
    rawKey: String,
    definition: ParticipantTagDefinitionDto?,
    modifier: Modifier = Modifier,
    fontSize: Int = 11
) {
    val label = definition?.labelPl ?: humanizeKey(rawKey)
    val bgColor = TAILWIND_BG_MAP[definition?.colorBg] ?: DEFAULT_BG
    val textColor = TAILWIND_TEXT_MAP[definition?.colorText] ?: DEFAULT_TEXT

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = fontSize.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun humanizeKey(key: String): String =
    key.split('_').joinToString(" ") { word ->
        if (word.isEmpty()) word else word.replaceFirstChar { it.titlecase() }
    }

private val DEFAULT_BG = Color(0xFFF3F4F6)
private val DEFAULT_TEXT = Color(0xFF4B5563)

/**
 * Tailwind bg-{color}-500/10 -> Compose Color (jasne tlo ~10% alpha equivalent).
 * Wartosci wzorowane na desktop ParticipantTagBadge w shadcn/ui.
 */
private val TAILWIND_BG_MAP: Map<String, Color> = mapOf(
    "bg-blue-500/10" to Color(0xFFDBEAFE),
    "bg-purple-500/10" to Color(0xFFEDE9FE),
    "bg-orange-500/10" to Color(0xFFFFEDD5),
    "bg-indigo-500/10" to Color(0xFFE0E7FF),
    "bg-amber-500/10" to Color(0xFFFEF3C7),
    "bg-cyan-500/10" to Color(0xFFCFFAFE),
    "bg-rose-500/10" to Color(0xFFFFE4E6),
    "bg-emerald-500/10" to Color(0xFFD1FAE5),
    "bg-slate-500/10" to Color(0xFFE2E8F0),
    "bg-gray-500/10" to Color(0xFFF3F4F6),
    "bg-red-500/10" to Color(0xFFFEE2E2),
    "bg-green-500/10" to Color(0xFFDCFCE7),
    "bg-yellow-500/10" to Color(0xFFFEF9C3),
    "bg-pink-500/10" to Color(0xFFFCE7F3),
    "bg-teal-500/10" to Color(0xFFCCFBF1),
    "bg-lime-500/10" to Color(0xFFECFCCB),
    "bg-fuchsia-500/10" to Color(0xFFFAE8FF),
    "bg-violet-500/10" to Color(0xFFEDE9FE),
    "bg-sky-500/10" to Color(0xFFE0F2FE),
)

private val TAILWIND_TEXT_MAP: Map<String, Color> = mapOf(
    "text-blue-700" to Color(0xFF1D4ED8),
    "text-purple-700" to Color(0xFF7E22CE),
    "text-orange-700" to Color(0xFFC2410C),
    "text-indigo-700" to Color(0xFF4338CA),
    "text-amber-700" to Color(0xFFB45309),
    "text-cyan-700" to Color(0xFF0E7490),
    "text-rose-700" to Color(0xFFBE123C),
    "text-emerald-700" to Color(0xFF047857),
    "text-slate-700" to Color(0xFF334155),
    "text-gray-600" to Color(0xFF4B5563),
    "text-gray-700" to Color(0xFF374151),
    "text-red-700" to Color(0xFFB91C1C),
    "text-green-700" to Color(0xFF15803D),
    "text-yellow-700" to Color(0xFFA16207),
    "text-pink-700" to Color(0xFFBE185D),
    "text-teal-700" to Color(0xFF0F766E),
    "text-lime-700" to Color(0xFF4D7C0F),
    "text-fuchsia-700" to Color(0xFFA21CAF),
    "text-violet-700" to Color(0xFF6D28D9),
    "text-sky-700" to Color(0xFF0369A1),
)
