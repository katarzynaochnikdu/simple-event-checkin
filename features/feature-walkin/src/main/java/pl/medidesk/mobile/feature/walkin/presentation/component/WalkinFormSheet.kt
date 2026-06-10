package pl.medidesk.mobile.feature.walkin.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.medidesk.mobile.core.model.TicketClass
import pl.medidesk.mobile.core.ui.components.SecureDialogEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkinFormSheet(
    ticketClasses: List<TicketClass>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String?, String?, String?, String?, String?, String?) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var selectedTicketClass by remember { mutableStateOf<TicketClass?>(null) }
    var notes by remember { mutableStateOf("") }
    var ticketClassMenuExpanded by remember { mutableStateOf(false) }
    var firstNameError by remember { mutableStateOf(false) }
    var lastNameError by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // WO-MOB-034 (N-3): formularz walk-in niesie PII gościa (imię, nazwisko,
        // email, telefon, firma, notatki) — okno chronione przed zrzutem w release.
        SecureDialogEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nowy gość walk-in", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = firstName, onValueChange = { firstName = it; firstNameError = false },
                label = { Text("Imię *") }, modifier = Modifier.fillMaxWidth(),
                isError = firstNameError, singleLine = true
            )
            OutlinedTextField(
                value = lastName, onValueChange = { lastName = it; lastNameError = false },
                label = { Text("Nazwisko *") }, modifier = Modifier.fillMaxWidth(),
                isError = lastNameError, singleLine = true
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Telefon") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = company, onValueChange = { company = it },
                label = { Text("Firma") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            if (ticketClasses.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = ticketClassMenuExpanded,
                    onExpandedChange = { ticketClassMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTicketClass?.ticketName ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategoria biletu") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ticketClassMenuExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = ticketClassMenuExpanded,
                        onDismissRequest = { ticketClassMenuExpanded = false }
                    ) {
                        ticketClasses.forEach { tc ->
                            DropdownMenuItem(
                                text = { Text(tc.ticketName) },
                                onClick = { selectedTicketClass = tc; ticketClassMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notatki") }, modifier = Modifier.fillMaxWidth(), maxLines = 3
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Anuluj") }
                Button(
                    onClick = {
                        if (firstName.isBlank()) { firstNameError = true; return@Button }
                        if (lastName.isBlank()) { lastNameError = true; return@Button }
                        onSubmit(
                            firstName.trim(), lastName.trim(),
                            email.trim().ifBlank { null }, phone.trim().ifBlank { null },
                            company.trim().ifBlank { null },
                            selectedTicketClass?.ticketClassId, selectedTicketClass?.ticketName,
                            notes.trim().ifBlank { null }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Zapisz")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
