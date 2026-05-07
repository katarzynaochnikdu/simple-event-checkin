package pl.medidesk.mobile.feature.sponsors.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.core.model.EventSponsor
import pl.medidesk.mobile.feature.sponsors.presentation.viewmodel.SponsorsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorsScreen(
    onNavigateBack: () -> Unit,
    onSponsorClick: (Long) -> Unit,
    viewModel: SponsorsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sponsorzy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Szukaj sponsora...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(uiState.error ?: "Błąd", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::loadSponsors) { Text("Ponów") }
                        }
                    }
                }
                uiState.filteredSponsors.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (uiState.searchQuery.isNotBlank()) "Brak wyników"
                            else "Brak sponsorów",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Text(
                        "${uiState.filteredSponsors.size} sponsorów",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.filteredSponsors, key = { it.eventSponsorId }) { sponsor ->
                            SponsorCard(
                                sponsor = sponsor,
                                onClick = { onSponsorClick(sponsor.eventSponsorId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorCard(sponsor: EventSponsor, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo or initials
            MdAsyncImage(
                model = sponsor.companyLogoUrl,
                contentDescription = sponsor.companyName,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
                initials = sponsor.companyName.take(2),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sponsor.companyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sponsor.industryCategory.isNotBlank() && sponsor.industryCategory != "other") {
                    Text(
                        sponsor.industryCategory.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Package badge
                    if (!sponsor.packageLabel.isNullOrBlank()) {
                        val badgeColor = try {
                            Color(android.graphics.Color.parseColor(sponsor.packageColor ?: "#94a3b8"))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                sponsor.packageLabel!!,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    // Status chip
                    StatusChip(sponsor.pipelineStatus)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    if (status.isBlank()) return
    val (label, color) = when (status) {
        "won" -> "Potwierdzony" to Color(0xFF22C55E)
        "lead" -> "Lead" to Color(0xFF3B82F6)
        "negotiation" -> "Negocjacje" to Color(0xFFF59E0B)
        "proposal_sent" -> "Oferta" to Color(0xFF8B5CF6)
        "lost" -> "Utracony" to Color(0xFFEF4444)
        else -> status.replaceFirstChar { it.uppercase() } to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
