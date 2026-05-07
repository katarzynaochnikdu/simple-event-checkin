package pl.medidesk.mobile.feature.speakers.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.core.model.Speaker
import pl.medidesk.mobile.feature.speakers.presentation.viewmodel.SpeakerDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: SpeakerDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.speaker?.displayName ?: "Prelegent") },
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
            uiState.speaker != null -> {
                val speaker = uiState.speaker!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Photo / Avatar
                    MdAsyncImage(
                        model = speaker.photoUrl.ifBlank { null },
                        contentDescription = speaker.displayName,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        initials = speaker.initials,
                        shape = CircleShape,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Name
                    Text(
                        speaker.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Organization & Affiliation
                    if (speaker.organization.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            speaker.organization,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (speaker.affiliation.isNotBlank()) {
                        Text(
                            speaker.affiliation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Contact section
                    val hasContact = speaker.email.isNotBlank() || speaker.phone.isNotBlank()
                    if (hasContact) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Kontakt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                if (speaker.email.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Email,
                                        label = speaker.email,
                                        onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${speaker.email}"))) }
                                    )
                                }
                                if (speaker.phone.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Phone,
                                        label = speaker.phone,
                                        onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${speaker.phone}"))) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Social links
                    val hasSocial = speaker.socialLinkedin.isNotBlank() ||
                        speaker.socialTwitter.isNotBlank() ||
                        speaker.website.isNotBlank()
                    if (hasSocial) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Social", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                if (speaker.socialLinkedin.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Person,
                                        label = "LinkedIn",
                                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(speaker.socialLinkedin))) }
                                    )
                                }
                                if (speaker.socialTwitter.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Share,
                                        label = "Twitter / X",
                                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(speaker.socialTwitter))) }
                                    )
                                }
                                if (speaker.website.isNotBlank()) {
                                    ContactRow(
                                        icon = Icons.Default.Info,
                                        label = speaker.website,
                                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(speaker.website))) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Bio
                    val bioText = speaker.bioLong.ifBlank { speaker.bio }
                    if (bioText.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Bio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(bioText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onClick) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
