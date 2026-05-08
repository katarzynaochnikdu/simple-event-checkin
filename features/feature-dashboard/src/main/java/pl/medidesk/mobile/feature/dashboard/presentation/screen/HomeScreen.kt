package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.feature.dashboard.presentation.viewmodel.DashboardUiState
import pl.medidesk.mobile.feature.dashboard.presentation.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEvents: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToAttractions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user = (uiState as? DashboardUiState.Success)?.user

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Text("MD", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Panel Sterowania", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Wyloguj", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
        ) {
            Text("Dzień dobry,", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(user?.firstName?.ifBlank { "Organizatorze" } ?: "Użytkowniku", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

            Spacer(Modifier.height(32.dp))

            val menuItems = listOf(
                HomeMenuItem("Skaner QR", Icons.Default.QrCodeScanner, MaterialTheme.colorScheme.primary, onNavigateToScanner),
                HomeMenuItem("Wydarzenia", Icons.Default.Event, MaterialTheme.colorScheme.secondary, onNavigateToEvents),
                // Attractions and Settings hidden in Simple mode
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(menuItems) { item -> HomeMenuTile(item) }
            }
            
            Spacer(Modifier.weight(1f))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Zalogowano jako: ${user?.role ?: "Nieznana rola"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

data class HomeMenuItem(val title: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

@Composable
private fun HomeMenuTile(item: HomeMenuItem) {
    Card(onClick = item.onClick, modifier = Modifier.fillMaxWidth().height(130.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = item.color.copy(alpha = 0.12f)) {
                Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.padding(14.dp).size(28.dp), tint = item.color)
            }
            Spacer(Modifier.height(16.dp))
            Text(text = item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
