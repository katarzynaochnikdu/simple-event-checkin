package pl.medidesk.mobile.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WO-MOB-037: lista uprawnień (nazwy biletów) pod jednym kodem osoby.
 */
@Composable
fun TicketNameChips(
    names: List<String>,
    modifier: Modifier = Modifier,
    chipColor: Color = MaterialTheme.colorScheme.primaryContainer,
    chipContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    spacing: Dp = 6.dp
) {
    if (names.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = horizontalAlignment
    ) {
        names.forEach { name ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = chipColor
            ) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = chipContentColor
                )
            }
        }
    }
}

@Composable
fun TicketNamesPlain(
    names: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    prefix: String = "Uprawnienia: "
) {
    if (names.isEmpty()) return
    Column(modifier = modifier) {
        names.forEachIndexed { index, name ->
            Text(
                text = if (index == 0 && names.size == 1) "Bilet: $name" else if (index == 0) "$prefix$name" else "• $name",
                fontSize = 12.sp,
                color = color
            )
        }
    }
}
