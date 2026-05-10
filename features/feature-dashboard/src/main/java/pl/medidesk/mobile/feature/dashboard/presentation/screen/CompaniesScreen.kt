package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.core.ui.theme.StatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.CompanyDto
import pl.medidesk.mobile.core.network.dto.CompanyPersonDto
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class CompaniesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val companies: List<CompanyDto> = emptyList(),
    val totalCompanies: Int = 0
)

@HiltViewModel
class CompaniesViewModel @Inject constructor(
    private val api: MobileApiService
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompaniesUiState())
    val uiState = _uiState.asStateFlow()

    fun load(eventId: String, role: String = "participant") {
        viewModelScope.launch {
            _uiState.value = CompaniesUiState(isLoading = true)
            try {
                val response = api.getCompanies(eventId, role)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.value = CompaniesUiState(
                        isLoading = false,
                        companies = body.companies,
                        totalCompanies = body.totalCompanies
                    )
                } else {
                    _uiState.value = CompaniesUiState(isLoading = false, error = "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = CompaniesUiState(isLoading = false, error = e.message ?: "Nieznany błąd")
            }
        }
    }
}

// ─── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaniesScreen(
    eventId: String,
    role: String = "participant",
    title: String = "Firmy — Uczestnicy",
    onNavigateBack: () -> Unit,
    viewModel: CompaniesViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(eventId, role) {
        viewModel.load(eventId, role)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    Text(
                        "${state.totalCompanies} firm",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.load(eventId, role) }) {
                            Text("Ponów")
                        }
                    }
                }
                state.companies.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Business, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Brak firm", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Żadna firma nie jest powiązana z tym wydarzeniem", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.companies, key = { it.companyName }) { company ->
                            CompanyCard(company)
                        }
                    }
                }
            }
        }
    }
}

// ─── Company Card ──────────────────────────────────────────────────────────────

@Composable
private fun CompanyCard(company: CompanyDto) {
    var expanded by remember { mutableStateOf(false) }
    val checkedIn = company.checkedInCount
    val total = company.participantCount
    val rate = if (total > 0) (checkedIn * 100.0 / total) else 0.0
    val persons = company.persons ?: emptyList()

    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Logo or initial
                MdAsyncImage(
                    model = company.logoUrl,
                    contentDescription = company.companyName,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                    initials = company.companyName.take(2),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(company.companyName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Role pill — używa tokenów theme zamiast surowych kolorów
                        val (roleLabel, roleColor) = when (company.role) {
                            "sponsor" -> "Sponsor" to MaterialTheme.colorScheme.tertiary
                            "participant" -> "Uczestnicy" to MaterialTheme.colorScheme.primary
                            else -> company.role to mutedColor
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(roleColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(roleLabel, fontSize = 10.sp, color = roleColor, fontWeight = FontWeight.SemiBold)
                        }
                        if (company.industry != null) {
                            Spacer(Modifier.width(6.dp))
                            Text("· ${company.industry}", fontSize = 11.sp, color = mutedColor)
                        }
                    }
                }
                // Stats
                Column(horizontalAlignment = Alignment.End) {
                    Text("$total", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = accentColor)
                    Text("osób", fontSize = 10.sp, color = mutedColor)
                }
            }

            // Check-in progress
            if (total > 0 && company.role == "participant") {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { (rate / 100.0).toFloat() },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${checkedIn}/${total}", fontSize = 11.sp, color = mutedColor, fontWeight = FontWeight.Medium)
                }
            }

            // Expanded: person list
            if (expanded && persons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                persons.forEach { person ->
                    PersonRow(person, company.role)
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Expand hint
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = mutedColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonRow(person: CompanyPersonDto, companyRole: String) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${(person.firstName ?: "").take(1)}${(person.lastName ?: "").take(1)}".uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${person.firstName ?: ""} ${person.lastName ?: ""}".trim().ifEmpty { "—" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val email = person.email
            if (!email.isNullOrBlank()) {
                Text(email, fontSize = 11.sp, color = mutedColor)
            }
        }
        // Status indicators — używają StatusColors z theme
        if (companyRole == "participant") {
            val checkedIn = person.checkedIn == true
            val statusColor = if (checkedIn) StatusColors.Paid else StatusColors.Pending
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (checkedIn) "✓" else "—",
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        val pos = person.position
        if (!pos.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(pos, fontSize = 10.sp, color = mutedColor)
        }
    }
}
