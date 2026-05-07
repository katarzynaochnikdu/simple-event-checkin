package pl.medidesk.mobile.feature.inhub.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.ui.components.LoadingScreen
import pl.medidesk.mobile.core.ui.theme.ScanDuplicate
import pl.medidesk.mobile.core.ui.theme.ScanError
import pl.medidesk.mobile.core.ui.theme.ScanSuccess
import pl.medidesk.mobile.feature.inhub.presentation.viewmodel.InHubMode
import pl.medidesk.mobile.feature.inhub.presentation.viewmodel.InHubViewModel

@Composable
fun InHubScreen(
    eventId: String,
    viewModel: InHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) { viewModel.loadConfig(eventId) }

    when (uiState.mode) {
        InHubMode.SETUP -> InHubSetupScreen(
            isLoading = uiState.isLoading,
            error = uiState.error,
            onSave = { pin, autoCheckin, showSearch, showWalkin ->
                viewModel.saveConfig(eventId, pin, autoCheckin, showSearch, showWalkin)
            },
            onBypass = viewModel::bypassAndStart
        )
        InHubMode.PIN_LOCK -> PinLockScreen(
            pin = uiState.pin,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onPinChange = viewModel::onPinChanged,
            onVerify = { viewModel.verifyPin(eventId, uiState.pin) }
        )
        InHubMode.ACTIVE -> InHubActiveScreen(
            eventId = eventId,
            onQrScanned = { ticketId -> viewModel.onQrScanned(ticketId, eventId) },
            onLock = viewModel::lock
        )
        InHubMode.RESULT -> {
            val result = uiState.lastCheckinResult
            val (bgColor, label) = when {
                result == null -> Color.Gray to "..."
                !result.success -> ScanError to "NIE ZNALEZIONO"
                result.alreadyCheckedIn -> ScanDuplicate to "JUŻ ZAREJESTROWANY"
                else -> ScanSuccess to "WEJŚCIE OK"
            }
            Box(
                Modifier.fillMaxSize().background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.headlineLarge,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    result?.participant?.let { p ->
                        Spacer(Modifier.height(16.dp))
                        Text(p.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(p.ticketName, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PinLockScreen(
    pin: String, isLoading: Boolean, error: String?,
    onPinChange: (String) -> Unit, onVerify: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp))
            Text("InHub Mode — zablokowany", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) onPinChange(it) },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                isError = error != null
            )
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = onVerify, enabled = pin.isNotBlank() && !isLoading) {
                Text("Odblokuj InHub")
            }
        }
    }
}

@Composable
private fun InHubSetupScreen(
    isLoading: Boolean, error: String?,
    onSave: (String, Boolean, Boolean, Boolean) -> Unit,
    onBypass: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var autoCheckin by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(true) }
    var showWalkin by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Konfiguracja InHub Mode", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = pin, onValueChange = { if (it.length <= 8) pin = it },
                label = { Text("PIN (min. 4 cyfry)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Automatyczny check-in")
                Switch(checked = autoCheckin, onCheckedChange = { autoCheckin = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Wyszukiwarka")
                Switch(checked = showSearch, onCheckedChange = { showSearch = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rejestracja walk-in")
                Switch(checked = showWalkin, onCheckedChange = { showWalkin = it })
            }
            
            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Błąd serwera", fontWeight = FontWeight.Bold, color = Color.Red)
                        }
                        Text(error, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                        TextButton(onClick = onBypass, modifier = Modifier.align(Alignment.End)) {
                            Text("Uruchom mimo błędu (tryb lokalny)")
                        }
                    }
                }
            }

            Button(
                onClick = { onSave(pin, autoCheckin, showSearch, showWalkin) },
                enabled = pin.length >= 4 && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Aktywuj InHub Mode")
            }
        }
    }
}

@Composable
private fun InHubActiveScreen(
    eventId: String,
    onQrScanned: (String) -> Unit,
    onLock: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Text(
            "InHub Mode — AKTYWNY\nSkanuj QR kod...",
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(
            onClick = onLock,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = "Zablokuj", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
