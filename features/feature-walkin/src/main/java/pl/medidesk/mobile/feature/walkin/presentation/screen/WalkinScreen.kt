package pl.medidesk.mobile.feature.walkin.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.model.WalkinParticipant
import pl.medidesk.mobile.core.ui.theme.ScanSuccess
import pl.medidesk.mobile.feature.walkin.presentation.component.WalkinFormSheet
import pl.medidesk.mobile.feature.walkin.presentation.viewmodel.WalkinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkinScreen(
    eventId: String,
    viewModel: WalkinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) { viewModel.loadData(eventId) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showForm) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj gościa")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.walkins.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Brak walk-inów. Tap + aby dodać.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(uiState.walkins, key = { it.id }) { walkin ->
                        WalkinRow(walkin)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (uiState.showForm) {
        WalkinFormSheet(
            ticketClasses = uiState.ticketClasses,
            isLoading = uiState.isLoading,
            onDismiss = viewModel::hideForm,
            onSubmit = { firstName, lastName, email, phone, company, ticketClassId, ticketName, notes ->
                viewModel.createWalkin(eventId, firstName, lastName, email, phone, company, ticketClassId, ticketName, notes)
            }
        )
    }
}

@Composable
private fun WalkinRow(walkin: WalkinParticipant) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (walkin.checkedInAt != null) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
            contentDescription = null,
            tint = if (walkin.checkedInAt != null) ScanSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${walkin.firstName} ${walkin.lastName}", style = MaterialTheme.typography.bodyLarge)
            val company = walkin.company
            if (!company.isNullOrBlank()) {
                Text(company, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (walkin.syncStatus == "pending") {
            Badge(containerColor = MaterialTheme.colorScheme.errorContainer) { Text("⏳") }
        }
    }
}
