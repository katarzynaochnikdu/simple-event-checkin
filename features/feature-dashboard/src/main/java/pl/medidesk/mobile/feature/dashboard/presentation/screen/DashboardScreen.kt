package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import pl.medidesk.mobile.feature.addorder.presentation.screen.AddOrderSheet
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
    onEventColorLoaded: (Color) -> Unit = {},
    onEventNameLoaded: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) { viewModel.loadDashboard(eventId) }

    LifecycleResumeEffect(eventId) {
        viewModel.loadDashboard(eventId)
        onPauseOrDispose {}
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> LoadingScreen("Ładowanie...")
            is DashboardUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.message) }
            is DashboardUiState.Success -> {
                val eventColor = pl.medidesk.mobile.feature.events.presentation.screen.parseHexColor(state.data.primaryColor)
                LaunchedEffect(eventColor) { if (eventColor != null) onEventColorLoaded(eventColor) }
                LaunchedEffect(state.data.eventName) {
                    if (state.data.eventName.isNotBlank()) onEventNameLoaded(state.data.eventName)
                }
                // simple-event-checkin to apka tylko dla operatorów/organizatorów —
                // każdy zalogowany user widzi panel zarządzania, bez "trybu uczestnika".
                OrganizerDashboard(
                    eventId = eventId,
                    data = state.data,
                    onScannerClick = onNavigateToScanner,
                    onParticipantsClick = onNavigateToParticipants,
                    onStatsClick = onNavigateToStats,
                    onMyMenteesClick = onNavigateToMyMentees,
                    onSpeakersClick = onNavigateToSpeakers,
                    onBackToEvents = onBackToEvents
                )
            }
        }
    }
}

@Composable
private fun OrganizerDashboard(
    eventId: String,
    data: DashboardData,
    onScannerClick: () -> Unit,
    onParticipantsClick: (filterType: String?) -> Unit,
    onStatsClick: () -> Unit,
    onMyMenteesClick: () -> Unit,
    onSpeakersClick: () -> Unit,
    onBackToEvents: () -> Unit
) {
    var showAddOrderSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        DashboardHeader(data, onBackToEvents = onBackToEvents)
        Column(modifier = Modifier.padding(top = 16.dp)) {
            ProgressCard(data)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(data.checkedIn.toString(), "ODZNACZENI", StatusColors.Paid, Modifier.weight(1f)) { onParticipantsClick("checkedIn") }
                SummaryCard((data.totalRegistered - data.checkedIn).toString(), "OCZEKUJĄCY", StatusColors.Pending, Modifier.weight(1f)) { onParticipantsClick("pending") }
                SummaryCard(data.totalRegistered.toString(), "ŁĄCZNIE", MaterialTheme.colorScheme.onBackground, Modifier.weight(1f)) { onParticipantsClick(null) }
            }
        }
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MenuButton("Uczestnicy", Icons.Default.Group, MaterialTheme.colorScheme.secondary, { onParticipantsClick(null) }, Modifier.weight(1f))
            MenuButton("Dodaj uczestnika", Icons.Default.PersonAdd, MaterialTheme.colorScheme.primary, { showAddOrderSheet = true }, Modifier.weight(1f))
        }
        // WO-MOB-015 hotfix2 (2026-05-25): full-width Prelegenci tile w kolorze ProgressCard,
        // białe liczby + ikona mówcy. Pozycja: między rzędami akcji (po Uczestnikach, przed Moimi podopiecznymi).
        if (data.speakersTotal > 0) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                SpeakersCard(
                    attended = data.speakersAttended,
                    total = data.speakersTotal,
                    primaryColor = data.primaryColor,
                    onClick = onSpeakersClick
                )
            }
        }
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MenuButton("Moi podopieczni", Icons.Default.SupervisorAccount, MaterialTheme.colorScheme.primary, onMyMenteesClick, Modifier.weight(1f))
            MenuButton("Statystyki", Icons.Default.BarChart, MaterialTheme.colorScheme.tertiary, onStatsClick, Modifier.weight(1f))
        }
    }

    if (showAddOrderSheet) {
        AddOrderSheet(
            eventId = eventId,
            onDismiss = { showAddOrderSheet = false },
            onSuccess = { onParticipantsClick(null) }
        )
    }
}

@Composable
private fun DashboardHeader(data: DashboardData, onBackToEvents: () -> Unit = {}) {
    val contentColor = MaterialTheme.colorScheme.onBackground
    val secondaryColor = contentColor.copy(alpha = 0.7f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconButton(onClick = onBackToEvents) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wróć",
                tint = contentColor
            )
        }
        Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
            Text(
                text = data.eventName.ifEmpty { "Wydarzenie" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = secondaryColor)
                Spacer(Modifier.width(6.dp))
                Text(formatDateLabel(data.startDate), style = MaterialTheme.typography.bodySmall, color = secondaryColor)
                if (data.venue.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = secondaryColor)
                    Spacer(Modifier.width(4.dp))
                    Text(data.venue, style = MaterialTheme.typography.bodySmall, color = secondaryColor)
                }
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
private fun MenuButton(title: String, icon: ImageVector, iconColor: Color, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier.height(72.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(10.dp), color = iconColor.copy(alpha = 0.1f)) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// WO-MOB-015 hotfix2 (2026-05-25): pełnoszerokościowy kafelek "Prelegenci K/N"
// w kolorze wydarzenia (parseHexColor — identyczny pattern jak ProgressCard).
// Białe liczby + ikona mówcy (Mic) + label "PRELEGENCI".
@Composable
private fun SpeakersCard(attended: Int, total: Int, primaryColor: String?, onClick: () -> Unit) {
    val cardColor = pl.medidesk.mobile.feature.events.presentation.screen.parseHexColor(primaryColor)
        ?: MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "$attended/$total",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "PRELEGENCI",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

