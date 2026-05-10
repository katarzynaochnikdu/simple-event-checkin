package pl.medidesk.mobile.feature.addorder.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.feature.addorder.presentation.state.PayerFormData

@Composable
fun PayerFieldsForm(
    payer: PayerFormData,
    errors: Map<String, String>,
    isLookingUpGus: Boolean,
    gusError: String?,
    onPayerChange: ((PayerFormData) -> PayerFormData) -> Unit,
    onLookupGus: (String) -> Unit,
    onCopyFromParticipant: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dane płatnika",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onCopyFromParticipant) {
                Icon(Icons.Default.ContentCopy, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Skopiuj z uczestnika")
            }
        }

        OutlinedTextField(
            value = payer.firstName,
            onValueChange = { v -> onPayerChange { it.copy(firstName = v) } },
            label = { Text("Imię *") },
            isError = errors["firstName"] != null,
            supportingText = errors["firstName"]?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = payer.lastName,
            onValueChange = { v -> onPayerChange { it.copy(lastName = v) } },
            label = { Text("Nazwisko *") },
            isError = errors["lastName"] != null,
            supportingText = errors["lastName"]?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = payer.email,
            onValueChange = { v -> onPayerChange { it.copy(email = v) } },
            label = { Text("Email *") },
            isError = errors["email"] != null,
            supportingText = errors["email"]?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = payer.phone,
            onValueChange = { v -> onPayerChange { it.copy(phone = v) } },
            label = { Text("Telefon") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()) {
            Text("Płatnik to firma", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge)
            Switch(checked = payer.isCompany,
                onCheckedChange = { v -> onPayerChange { it.copy(isCompany = v) } })
        }

        AnimatedVisibility(visible = payer.isCompany) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = payer.nip,
                        onValueChange = { v -> onPayerChange { it.copy(nip = v.filter { c -> c.isDigit() }.take(10)) } },
                        label = { Text("NIP *") },
                        isError = errors["nip"] != null || gusError != null,
                        supportingText = (errors["nip"] ?: gusError)?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onLookupGus(payer.nip) },
                        enabled = payer.nip.length == 10 && !isLookingUpGus,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (isLookingUpGus) {
                            CircularProgressIndicator(strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("GUS")
                        }
                    }
                }
                OutlinedTextField(
                    value = payer.companyName,
                    onValueChange = { v -> onPayerChange { it.copy(companyName = v) } },
                    label = { Text("Nazwa firmy *") },
                    isError = errors["companyName"] != null,
                    supportingText = errors["companyName"]?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = payer.address,
                    onValueChange = { v -> onPayerChange { it.copy(address = v) } },
                    label = { Text("Adres (ulica i numer)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = payer.zip,
                        onValueChange = { v -> onPayerChange { it.copy(zip = v) } },
                        label = { Text("Kod pocztowy") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = payer.city,
                        onValueChange = { v -> onPayerChange { it.copy(city = v) } },
                        label = { Text("Miasto") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                }
                OutlinedTextField(
                    value = payer.invoiceComment,
                    onValueChange = { v ->
                        onPayerChange { it.copy(invoiceComment = v.take(500)) }
                    },
                    label = { Text("Komentarz na fakturze (max 500)") },
                    isError = errors["invoiceComment"] != null,
                    supportingText = errors["invoiceComment"]?.let { { Text(it) } }
                        ?: { Text("${payer.invoiceComment.length}/500") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
