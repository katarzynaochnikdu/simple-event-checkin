package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.feature.addorder.domain.OrderTicketClass
import pl.medidesk.mobile.feature.addorder.presentation.state.AppliedDiscount

@Composable
fun DiscountCodeForm(
    selectedTicket: OrderTicketClass?,
    discountCode: String,
    appliedDiscount: AppliedDiscount?,
    isValidating: Boolean,
    error: String?,
    finalGross: Double,
    onCodeChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Kod rabatowy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Text("Opcjonalnie — możesz pominąć ten krok.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = discountCode,
                onValueChange = onCodeChange,
                label = { Text("Kod") },
                placeholder = { Text("np. EARLY20") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                enabled = appliedDiscount == null && !isValidating,
                modifier = Modifier.weight(1f)
            )
            if (appliedDiscount == null) {
                Button(
                    onClick = onApply,
                    enabled = discountCode.trim().isNotBlank() && !isValidating,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Zastosuj")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Usuń")
                }
            }
        }

        // Box z kalkulacją
        if (selectedTicket != null) {
            val gross = selectedTicket.priceGross
            val discountAmount = gross - finalGross
            val isFree = finalGross <= 0.0001
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFree) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (appliedDiscount != null) BorderStroke(
                    1.dp, MaterialTheme.colorScheme.primary
                ) else null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (appliedDiscount != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(appliedDiscount.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    PriceRow("Cena biletu", gross)
                    if (appliedDiscount != null && discountAmount > 0) {
                        PriceRow(
                            "Rabat (${appliedDiscount.code})",
                            -discountAmount,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Do zapłaty",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (isFree) "Bezpłatne" else "%.2f zł".format(finalGross),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isFree) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, amount: Double, color: androidx.compose.ui.graphics.Color? = null) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Text("%.2f zł".format(amount),
            style = MaterialTheme.typography.bodyMedium,
            color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}
