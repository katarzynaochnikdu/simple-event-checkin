package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onDecrement: () -> Unit = {},
    // WO-175: error walidacji liczby (np. "Minimum 2 biletów wymagane").
    quantityError: String? = null
) {
    // WO-175: filtruj niedostępne ticket classes — nie pokazujemy w ogóle.
    val availableTickets = ticketClasses.filter { it.available }
    // WO-176: collapsible descriptions per karta (per-ticket-id state).
    val expandedDescriptions = rememberSaveable(
        saver = androidx.compose.runtime.saveable.mapSaver(
            save = { map -> map.toMap() },
            restore = { saved -> mutableStateMapOf<String, Boolean>().apply {
                saved.forEach { (k, v) -> put(k, v as Boolean) }
            } }
        )
    ) { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Wybierz typ biletu",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (availableTickets.isEmpty()) {
            Text(
                "Brak dostępnych typów biletów dla tego wydarzenia.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        availableTickets.forEach { tc ->
            val isSelected = tc.id == selectedId
            Card(
                onClick = { onSelect(tc.id) },
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
                    RadioButton(selected = isSelected, onClick = { onSelect(tc.id) })
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tc.name, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge)
                        // WO-175 iter 3: min/max info jako prominent chip TUŻ POD nazwą biletu
                        // (zamiast pod opisem) — żeby było wyraźne i nie tonąło w długich
                        // opisach. Background z accent color + icon biletu.
                        val minMaxLabel = formatMinMaxLabel(tc)
                        if (minMaxLabel != null) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ConfirmationNumber,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        minMaxLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        // WO-176: collapsible description — 2 linijki + "Zobacz więcej"/"Zwiń".
                        if (!tc.description.isNullOrBlank()) {
                            val isExpanded = expandedDescriptions[tc.id] == true
                            Text(
                                tc.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                            )
                            // Toggle "Zobacz więcej" / "Zwiń" — pokazuj zawsze gdy opis > 80 znaków
                            // (heurystyka: krótkie opisy mieszczą się w 2 linijkach bez ellipsis).
                            if (tc.description.length > 80) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isExpanded) "Zwiń ▲" else "Zobacz więcej ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        expandedDescriptions[tc.id] = !isExpanded
                                    }
                                )
                            }
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
        // WO-175: ukryj stepper gdy min == max (brak wyboru).
        val selectedTicket = availableTickets.firstOrNull { it.id == selectedId }
        if (selectedTicket != null && selectedTicket.maxQuantity > selectedTicket.minQuantity) {
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
                        "Liczba biletów",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selectedTicket.minQuantity > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ten bilet wymaga minimum ${selectedTicket.minQuantity} ${pluralBilet(selectedTicket.minQuantity)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (selectedTicket.maxQuantity in 1..98) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Maksymalnie ${selectedTicket.maxQuantity} ${pluralBilet(selectedTicket.maxQuantity)} na zamówienie",
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
                            // WO-176: dolny limit to minQuantity (revert from iter2).
                            enabled = participantCount > selectedTicket.minQuantity,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zmniejsz liczbę biletów")
                        }
                        Spacer(Modifier.width(24.dp))
                        Text(
                            "$participantCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (quantityError != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 48.dp)
                        )
                        Spacer(Modifier.width(24.dp))
                        FilledIconButton(
                            onClick = onIncrement,
                            enabled = participantCount < selectedTicket.maxQuantity,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zwiększ liczbę biletów")
                        }
                    }
                    // WO-175: error message gdy count < minQuantity (z ViewModel walidacji).
                    if (quantityError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            quantityError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
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

// WO-175: helpers dla min/max label na karcie biletu.
// Iter 3: skrócone wersje dla chipa (compact format).
private fun formatMinMaxLabel(tc: OrderTicketClass): String? {
    val min = tc.minQuantity
    val max = tc.maxQuantity
    return when {
        // Brak ograniczenia (default backend max_quantity=999, min=1) — nie pokazuj
        min == 1 && max >= 99 -> null
        // min == max → "1 bilet" / "2 bilety"
        min == max -> "$min ${pluralBilet(min)}"
        // Tylko górna granica → "Maks. 5 biletów"
        min == 1 -> "Maks. $max ${pluralBilet(max)}"
        // Tylko dolna → "Min. 2 biletów"
        max >= 99 -> "Min. $min ${pluralBilet(min)}"
        // Pełen zakres → "2 – 10 biletów"
        else -> "$min – $max ${pluralBilet(max)}"
    }
}

private fun pluralBilet(n: Int): String = when {
    n == 1 -> "bilet"
    n in 2..4 -> "bilety"
    else -> "biletów"
}
