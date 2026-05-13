package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.feature.addorder.domain.OrderTicketClass

@Composable
fun TicketTypePicker(
    ticketClasses: List<OrderTicketClass>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    // WO-171: stepper liczby uczestników (widoczny po wyborze klasy biletu).
    participantCount: Int = 1,
    onIncrement: () -> Unit = {},
    onDecrement: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Wybierz typ biletu",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (ticketClasses.isEmpty()) {
            Text(
                "Brak dostępnych typów biletów dla tego wydarzenia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        ticketClasses.forEach { tc ->
            val isSelected = tc.id == selectedId
            Card(
                onClick = { if (tc.available) onSelect(tc.id) },
                enabled = tc.available,
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
                    RadioButton(selected = isSelected, onClick = { if (tc.available) onSelect(tc.id) })
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tc.name, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge)
                        if (!tc.description.isNullOrBlank()) {
                            Text(tc.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!tc.available) {
                            Text("(Niedostępne)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${"%.2f".format(tc.priceGross)} ${tc.currency}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "VAT ${tc.vatRate}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }

        // WO-171: Quantity stepper — pokazuje się dopiero po wyborze klasy biletu.
        val selectedTicket = ticketClasses.firstOrNull { it.id == selectedId }
        if (selectedTicket != null) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Liczba uczestników",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selectedTicket.minQuantity > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ten bilet wymaga minimum ${selectedTicket.minQuantity} uczestników",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (selectedTicket.maxQuantity in 1..98) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Maksymalnie ${selectedTicket.maxQuantity} biletów na zamówienie",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledIconButton(
                            onClick = onDecrement,
                            enabled = participantCount > selectedTicket.minQuantity,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zmniejsz liczbę uczestników")
                        }
                        Spacer(Modifier.width(24.dp))
                        Text(
                            "$participantCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 48.dp)
                        )
                        Spacer(Modifier.width(24.dp))
                        FilledIconButton(
                            onClick = onIncrement,
                            enabled = participantCount < selectedTicket.maxQuantity,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zwiększ liczbę uczestników")
                        }
                    }
                    if (participantCount > 1) {
                        Spacer(Modifier.height(12.dp))
                        val subtotal = selectedTicket.priceGross * participantCount
                        Text(
                            "Łącznie: ${"%.2f".format(subtotal)} ${selectedTicket.currency}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
