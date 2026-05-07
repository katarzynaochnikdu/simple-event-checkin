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
        topBar = {
            TopAppBar(
                title = { Text("Analityka Wydarzenia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = Color(0xFFF8F9FA)
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Sekcja: Frekwencja w czasie (Timeline)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PRZYBYCIE W CZASIE", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    if (data.timeline.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Brak danych czasowych", color = Color.LightGray)
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
                                            .background(Color(0xFF152C5B))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(entry.hour, fontSize = 9.sp, color = Color.Gray)
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TOP FIRMY / ORGANIZACJE", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
                        Text("Brak danych o firmach", color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        companyStats.forEach { (name, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                Icon(Icons.Default.Business, null, tint = Color(0xFF1565C0), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("$count osób", fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Color(0xFFF1F3F5))
                        }
                    }
                }
            }
        }

        // 3. Sekcja: Skanerzy
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("WYDAJNOŚĆ SKANERÓW", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    
                    if (data.topScanners.isEmpty()) {
                        Text("Czekam na pierwsze skany...", color = Color.LightGray)
                    } else {
                        data.topScanners.forEach { scanner ->
                            ScannerBarRow(scanner.email, scanner.count, data.checkedIn)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        // 4. Sekcja: Kategorie Biletów
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("STRUKTURA BILETÓW (WEJŚCIA)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
                                color = Color(0xFF2E7D32),
                                trackColor = Color(0xFFF1F3F5)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerBarRow(label: String, count: Int, total: Int) {
    val name = label.substringBefore("@")
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            Text("$count skanów", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) count.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)),
            color = Color(0xFF152C5B),
            trackColor = Color(0xFFF8F9FA)
        )
    }
}
