package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.model.DashboardData
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.ui.components.LoadingScreen
import pl.medidesk.mobile.core.ui.theme.StatusColors
import pl.medidesk.mobile.feature.dashboard.presentation.viewmodel.DashboardUiState
import pl.medidesk.mobile.feature.dashboard.presentation.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    eventId: String,
    onBackClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) { viewModel.loadDashboard(eventId) }

    Scaffold(
        // Outer MainScreen Scaffold już zarezerwowało miejsce na NavigationBar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Statystyki wejścia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> LoadingScreen("Ładowanie danych...")
                is DashboardUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.message) }
                is DashboardUiState.Success -> StatsContent(
                    data = state.data,
                    syncState = state.syncState,
                    onSyncClick = { viewModel.triggerSync(eventId) }
                )
            }
        }
    }
}

@Composable
private fun StatsContent(data: DashboardData, syncState: SyncState, onSyncClick: () -> Unit) {
    // Theme color reads — wyciągnięte ponad LazyColumn (LazyListScope nie jest @Composable).
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val dividerColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Sekcja: Frekwencja w czasie (Timeline)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors,
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PRZYBYCIE W CZASIE", style = MaterialTheme.typography.labelMedium, color = labelColor)
                    Spacer(Modifier.height(16.dp))
                    if (data.timeline.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Brak danych czasowych", color = mutedColor)
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val maxCount = data.timeline.maxOfOrNull { it.count } ?: 1
                            data.timeline.forEach { entry ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val barHeight = (entry.count.toFloat() / maxCount.toFloat() * 80).dp
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColor)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(entry.hour, fontSize = 9.sp, color = labelColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Sekcja: Kto skąd jest (Struktura Firm)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors,
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TOP FIRMY / ORGANIZACJE", style = MaterialTheme.typography.labelMedium, color = labelColor)
                    Spacer(Modifier.height(16.dp))

                    val companyStats = data.recentCheckins
                        .mapNotNull { it.company }
                        .filter { it.isNotBlank() }
                        .groupingBy { it }
                        .eachCount()
                        .toList()
                        .sortedByDescending { it.second }
                        .take(5)

                    if (companyStats.isEmpty()) {
                        Text("Brak danych o firmach", color = mutedColor, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        companyStats.forEach { (name, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                Icon(Icons.Default.Business, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("$count osób", fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = dividerColor)
                        }
                    }
                }
            }
        }

        // 4. Sekcja: Kategorie Biletów
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = cardColors,
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("STRUKTURA BILETÓW (WEJŚCIA)", style = MaterialTheme.typography.labelMedium, color = labelColor)
                    Spacer(Modifier.height(16.dp))

                    data.byTicketClass.forEach { stat ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stat.ticketName, style = MaterialTheme.typography.bodySmall)
                                Text("${stat.checkedIn}/${stat.total}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            LinearProgressIndicator(
                                progress = { if (stat.total > 0) stat.checkedIn.toFloat() / stat.total else 0f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = StatusColors.Paid,
                                trackColor = dividerColor
                            )
                        }
                    }
                }
            }
        }
    }
}

