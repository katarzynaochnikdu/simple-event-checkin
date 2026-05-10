package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.feature.addorder.domain.OrderPaymentMethod

@Composable
fun PaymentMethodPicker(
    methods: List<OrderPaymentMethod>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sposób płatności",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Text("Klient otrzyma email z linkiem do płatności bezpośrednio od systemu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (methods.isEmpty()) {
            Text("Brak dostępnych metod płatności.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
        }
        methods.filter { it.available }.forEach { m ->
            val isSelected = m.id == selectedId
            val (icon, desc) = describe(m)
            Card(
                onClick = { onSelect(m.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isSelected, onClick = { onSelect(m.id) })
                    Spacer(Modifier.width(8.dp))
                    Icon(icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.label, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge)
                        Text(desc, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun describe(m: OrderPaymentMethod): Pair<ImageVector, String> = when (m.id) {
    "proforma" -> Icons.Default.Receipt to
        "Faktura proforma + PDF z numerem konta. Termin ${m.deadlineDays ?: 7} dni."
    "stripe" -> Icons.Default.CreditCard to
        "Klient otrzyma email z linkiem Stripe (karta / BLIK)."
    "free" -> Icons.Default.CheckCircle to
        "Bezpłatna rejestracja — uczestnik dodany od razu."
    else -> Icons.Default.Receipt to (m.label)
}
