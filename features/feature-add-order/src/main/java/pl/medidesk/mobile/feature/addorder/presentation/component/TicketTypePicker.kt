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
import androidx.compose.ui.text.style.TextDecoration
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
            // WO-296: derive availability from backend flags (sold_out + sales_end_date).
            // Backend already filters most window_closed cases in get_public_event_config,
            // but we keep a defensive check for race conditions (stale config + late submit).
            val salesEndPassed = isSalesEndPassed(tc.salesEndDate)
            val unavailable = tc.soldOut || salesEndPassed
            // WO-296: optional EB discount badge — only when EB window is open AND eb_pct > 0.
            val showEbBadge = tc.ebActive && tc.ebPct > 0.0 && !unavailable
            // Resolve effective gross — backend `final_gross` wins over local priceGross;
            // base_gross is the line-through reference when EB is active.
            val finalGross = tc.finalGross ?: tc.priceGross
            val baseGross = tc.baseGross ?: tc.priceGross
            val hasEbDiscount = showEbBadge && finalGross < baseGross
            Card(
                onClick = { if (!unavailable) onSelect(tc.id) },
                enabled = !unavailable,
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
                    RadioButton(
                        selected = isSelected,
                        onClick = { if (!unavailable) onSelect(tc.id) },
                        enabled = !unavailable
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tc.name, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f))
                            // WO-296: state badge (priority: window_closed > sold_out > EB).
                            when {
                                salesEndPassed -> StateBadge(
                                    text = "Sprzedaż zakończona",
                                    container = MaterialTheme.colorScheme.errorContainer,
                                    onContainer = MaterialTheme.colorScheme.onErrorContainer
                                )
                                tc.soldOut -> StateBadge(
                                    text = "Wyprzedane",
                                    container = MaterialTheme.colorScheme.errorContainer,
                                    onContainer = MaterialTheme.colorScheme.onErrorContainer
                                )
                                showEbBadge -> StateBadge(
                                    text = "Early Bird −${formatPct(tc.ebPct)}%",
                                    container = MaterialTheme.colorScheme.tertiaryContainer,
                                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
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
                        // WO-296: line-through base price when EB is active.
                        if (hasEbDiscount) {
                            Text(
                                "${"%.2f".format(baseGross)} ${tc.currency}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        Text(
                            "${"%.2f".format(finalGross)} ${tc.currency}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "VAT ${tc.vatRate}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // WO-296: EB deadline info (date-only PL format).
                        val ebDeadline = formatEbDeadline(tc.ebUntil)
                        if (hasEbDiscount && ebDeadline != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "do $ebDeadline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
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

// WO-296: small surface chip — used for EB / sold_out / sales-window badges.
@Composable
private fun StateBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

// WO-296: backend returns ISO datetime; parse defensively. Returns null
// when input is null/blank/invalid — caller decides not to render in that case.
private fun isSalesEndPassed(iso: String?): Boolean {
    if (iso.isNullOrBlank()) return false
    return try {
        val parsed = java.time.OffsetDateTime.parse(iso).toInstant()
        parsed.isBefore(java.time.Instant.now())
    } catch (_: Exception) {
        try {
            val ld = java.time.LocalDateTime.parse(iso)
            ld.atZone(java.time.ZoneId.systemDefault()).toInstant()
                .isBefore(java.time.Instant.now())
        } catch (_: Exception) {
            false
        }
    }
}

// WO-296: format `eb_until` as Polish "DD.MM.YYYY" (date-only, mirrors admin UI WO-291).
private fun formatEbDeadline(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val odt = java.time.OffsetDateTime.parse(iso)
        odt.toLocalDate().format(
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        )
    } catch (_: Exception) {
        try {
            val ld = java.time.LocalDate.parse(iso.take(10))
            ld.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (_: Exception) {
            null
        }
    }
}

// Format a percent value with no trailing ".0" when integer-valued.
// 10.0 → "10", 12.5 → "12.5"
private fun formatPct(pct: Double): String {
    return if (pct % 1.0 == 0.0) pct.toInt().toString() else "%.1f".format(pct)
}
