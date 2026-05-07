package pl.medidesk.mobile.feature.more.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.model.SyncStatus
import pl.medidesk.mobile.core.ui.components.SyncStatusBar
import pl.medidesk.mobile.feature.more.presentation.viewmodel.MoreViewModel

@Composable
fun MoreScreen(
    eventId: String,
    onLogout: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SyncStatusBar(syncState = uiState.syncState, isOffline = false)

        // Profile section
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row {
                    Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(uiState.userDisplayName, style = MaterialTheme.typography.titleMedium)
                        Text(uiState.userEmail, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Sync status
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Synchronizacja", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (uiState.syncState.status) {
                            SyncStatus.IDLE -> if (uiState.syncState.totalPending > 0) "${uiState.syncState.totalPending} oczekujących" else "Zsynchronizowano"
                            SyncStatus.SYNCING -> "Synchronizuję..."
                            SyncStatus.SUCCESS -> "Zsynchronizowano"
                            SyncStatus.ERROR -> "Błąd: ${uiState.syncState.errorMessage}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.weight(1f))

        // Logout button
        Button(
            onClick = { showLogoutDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Wyloguj się")
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Wylogowanie") },
            text = { Text("Czy na pewno chcesz się wylogować?") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(onLogout) }) { Text("Wyloguj") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Anuluj") }
            }
        )
    }
}
