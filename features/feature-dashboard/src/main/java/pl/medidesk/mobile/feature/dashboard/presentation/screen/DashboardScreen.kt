package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.core.model.*
import pl.medidesk.mobile.core.ui.components.LoadingScreen
import pl.medidesk.mobile.core.ui.theme.StatusColors
import pl.medidesk.mobile.feature.dashboard.presentation.viewmodel.DashboardUiState
import pl.medidesk.mobile.feature.dashboard.presentation.viewmodel.DashboardViewModel
import pl.medidesk.mobile.feature.events.presentation.screen.formatDateLabel

@Composable
fun DashboardScreen(
    eventId: String,
    onNavigateToScanner: () -> Unit,
    onNavigateToParticipants: (filterType: String?) -> Unit,
    onNavigateToInHub: () -> Unit = {},
    onNavigateToStats: () -> Unit,
    onNavigateToSpeakers: () -> Unit = {},
    onNavigateToSponsors: () -> Unit = {},
    onNavigateToCompanies: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToMyMentees: () -> Unit = {},
    onBackToEvents: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var forceOrganizerView by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) { viewModel.loadDashboard(eventId) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> LoadingScreen("Ładowanie...")
            is DashboardUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.message) }
            is DashboardUiState.Success -> {
                val role = state.user.role.uppercase()
                val isOrganizer = forceOrganizerView || 
                                 role.contains("ORG") || 
                                 role.contains("ADM") || 
                                 role.contains("STAFF")

                if (isOrganizer) {
                    OrganizerDashboard(
                        data = state.data,
                        syncState = state.syncState,
                        onScannerClick = onNavigateToScanner,
                        onParticipantsClick = onNavigateToParticipants,
                        onStatsClick = onNavigateToStats,
                        onMyMenteesClick = onNavigateToMyMentees,
                        onSyncClick = { viewModel.triggerSync(eventId) },
                        onBackToEvents = onBackToEvents
                    )
                } else {
                    ParticipantDashboard(
                        data = state.data,
                        user = state.user,
                        onForceOrganizer = { forceOrganizerView = true },
                        onBackToEvents = onBackToEvents
                    )
                }
            }
        }
    }
}

@Composable
private fun OrganizerDashboard(
    data: DashboardData,
    syncState: SyncState,
    onScannerClick: () -> Unit,
    onParticipantsClick: (filterType: String?) -> Unit,
    onStatsClick: () -> Unit,
    onMyMenteesClick: () -> Unit,
    onSyncClick: () -> Unit,
    onBackToEvents: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { DashboardHeader(data, "Panel Zarządzania", onBackToEvents = onBackToEvents) }
        item { 
            Column(modifier = Modifier.padding(top = 16.dp)) {
                ProgressCard(data)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(data.checkedIn.toString(), "ODZNACZENI", StatusColors.Paid, Modifier.weight(1f)) { onParticipantsClick("checkedIn") }
                    SummaryCard((data.totalRegistered - data.checkedIn).toString(), "OCZEKUJĄCY", StatusColors.Pending, Modifier.weight(1f)) { onParticipantsClick("pending") }
                    SummaryCard(data.totalRegistered.toString(), "ŁĄCZNIE", MaterialTheme.colorScheme.onBackground, Modifier.weight(1f)) { onParticipantsClick(null) }
                }
            }
        }
        item {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MenuButton("Moi podopieczni", "Twoi przypisani uczestnicy", Icons.Default.SupervisorAccount, MaterialTheme.colorScheme.primary, onMyMenteesClick, Modifier.weight(1f))
                MenuButton("Uczestnicy", "Lista i wyszukiwanie", Icons.Default.Group, MaterialTheme.colorScheme.secondary, { onParticipantsClick(null) }, Modifier.weight(1f))
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                MenuButton("Statystyki", "Analiza frekwencji", Icons.Default.BarChart, MaterialTheme.colorScheme.tertiary, onStatsClick, Modifier.fillMaxWidth())
            }
        }
        item { SyncButton(syncState, onSyncClick) }
    }
}

@Composable
private fun ParticipantDashboard(data: DashboardData, user: User, onForceOrganizer: () -> Unit, onBackToEvents: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { DashboardHeader(data, "Mój Panel", onBackToEvents = onBackToEvents) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MÓJ BILET", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.size(200.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.QrCode, null, modifier = Modifier.size(140.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(user.email, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Standard Ticket", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Status: ${user.role}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onForceOrganizer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PRZEŁĄCZ NA TRYB ORGANIZATORA", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(data: DashboardData, subtitle: String, onBackToEvents: () -> Unit = {}) {
    // Kolor wydarzenia (z API) z fallbackiem do theme primary
    val headerColor = pl.medidesk.mobile.feature.events.presentation.screen.parseHexColor(data.primaryColor)
        ?: MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
    ) {
        // Wiersz: strzałka wstecz
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToEvents) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wróć",
                    tint = Color.White
                )
            }
        }
        // Nazwa wydarzenia i meta-dane
        Text(
            text = data.eventName.ifEmpty { "Wydarzenie" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp)
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.width(6.dp))
            Text(formatDateLabel(data.startDate), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }
        if (data.venue.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(6.dp))
                Text(data.venue, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ProgressCard(data: DashboardData) {
    val cardColor = pl.medidesk.mobile.feature.events.presentation.screen.parseHexColor(data.primaryColor)
        ?: MaterialTheme.colorScheme.primary
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(24.dp)) {
        Box(modifier = Modifier.padding(24.dp)) {
            Column {
                Text("POSTĘP CHECK-IN", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                Text("${data.checkInRate.toInt()}%", color = Color.White, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(progress = { data.checkInRate.toFloat() / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Color.White.copy(alpha = 0.4f), trackColor = Color.White.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
private fun SummaryCard(value: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MenuButton(title: String, subtitle: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier.height(90.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(10.dp), color = iconColor.copy(alpha = 0.1f)) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SyncButton(syncState: SyncState, onSyncClick: () -> Unit) {
    Button(onClick = onSyncClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Synchronizuj (${syncState.totalPending} oczekujących)", fontWeight = FontWeight.Bold)
    }
}
