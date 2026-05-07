package pl.medidesk.mobile.feature.sponsors.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.core.model.ContactPerson
import pl.medidesk.mobile.core.model.SponsorDetail
import pl.medidesk.mobile.feature.sponsors.presentation.viewmodel.SponsorDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: SponsorDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.detail?.company?.name ?: "Sponsor") },
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "Błąd", color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.detail != null -> {
                val detail = uiState.detail!!
                val company = detail.company

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Company header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Logo
                            MdAsyncImage(
                                model = company.logoUrl,
                                contentDescription = company.name,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit,
                                initials = company.name.take(2),
                            )
                            Spacer(Modifier.height(12.dp))

                            Text(
                                company.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (company.nip.isNotBlank()) {
                                Text(
                                    "NIP: ${company.nip}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Package badge
                            if (!detail.packageLabel.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                val badgeColor = try {
                                    Color(android.graphics.Color.parseColor(detail.packageColor ?: "#94a3b8"))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "Pakiet: ${detail.packageLabel}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = badgeColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Company info
                    val hasCompanyInfo = company.emailGeneral.isNotBlank() ||
                        company.phoneGeneral.isNotBlank() ||
                        company.website.isNotBlank() ||
                        company.addressCity.isNotBlank()

                    if (hasCompanyInfo) {
                        SectionCard(title = "Dane firmy") {
                            if (company.emailGeneral.isNotBlank()) {
                                InfoRow(Icons.Default.Email, company.emailGeneral) {
                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${company.emailGeneral}")))
                                }
                            }
                            if (company.phoneGeneral.isNotBlank()) {
                                InfoRow(Icons.Default.Phone, company.phoneGeneral) {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${company.phoneGeneral}")))
                                }
                            }
                            if (company.website.isNotBlank()) {
                                InfoRow(Icons.Default.Language, company.website) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(company.website)))
                                }
                            }
                            if (company.addressCity.isNotBlank()) {
                                val address = buildString {
                                    if (company.addressStreet.isNotBlank()) append("${company.addressStreet}, ")
                                    if (company.addressPostalCode.isNotBlank()) append("${company.addressPostalCode} ")
                                    append(company.addressCity)
                                }
                                InfoRow(Icons.Default.LocationOn, address)
                            }
                            if (company.industryCategory.isNotBlank() && company.industryCategory != "other") {
                                InfoRow(Icons.Default.Category, company.industryCategory.replaceFirstChar { it.uppercase() })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Contacts
                    if (detail.contacts.isNotEmpty()) {
                        SectionCard(title = "Osoby kontaktowe") {
                            detail.contacts.forEach { contact ->
                                ContactRow(contact = contact, context = context)
                                if (contact != detail.contacts.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Deal info
                    val hasDealInfo = detail.pipelineStatus.isNotBlank() ||
                        detail.contractValueNet != null ||
                        detail.opsStatus.isNotBlank()

                    if (hasDealInfo) {
                        SectionCard(title = "Współpraca") {
                            if (detail.pipelineStatus.isNotBlank()) {
                                LabeledValue("Status", detail.pipelineStatus.replaceFirstChar { it.uppercase() })
                            }
                            if (detail.dealType.isNotBlank()) {
                                LabeledValue("Typ", detail.dealType.replaceFirstChar { it.uppercase() })
                            }
                            if (detail.contractValueNet != null) {
                                LabeledValue("Wartość netto", "${String.format("%.2f", detail.contractValueNet)} PLN")
                            }
                            if (detail.opsStatus.isNotBlank()) {
                                LabeledValue("Status realizacji", detail.opsStatus.replace("_", " ").replaceFirstChar { it.uppercase() })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Tags
                    if (detail.tags.isNotEmpty()) {
                        SectionCard(title = "Tagi") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                detail.tags.take(5).forEach { tag ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        if (onClick != null) {
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ContactRow(contact: ContactPerson, context: android.content.Context) {
    Column {
        Text(
            contact.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (contact.position.isNotBlank()) {
            Text(
                contact.position,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (contact.email.isNotBlank()) {
            InfoRow(Icons.Default.Email, contact.email) {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${contact.email}")))
            }
        }
        if (contact.phone.isNotBlank()) {
            InfoRow(Icons.Default.Phone, contact.phone) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}")))
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
