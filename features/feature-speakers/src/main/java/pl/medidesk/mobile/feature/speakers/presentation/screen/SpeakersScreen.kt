package pl.medidesk.mobile.feature.speakers.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pl.medidesk.mobile.core.ui.components.MdAsyncImage
import pl.medidesk.mobile.core.model.Speaker
import pl.medidesk.mobile.feature.speakers.presentation.viewmodel.SpeakersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakersScreen(
    onNavigateBack: () -> Unit,
    onSpeakerClick: (String) -> Unit,
    viewModel: SpeakersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Push pending snackbar messages from ViewModel
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prelegenci") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                placeholder = { Text("Szukaj prelegenta...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Header — attended counter + "Tylko nieobecni" filter chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Obecni: ${uiState.attended}/${uiState.total.coerceAtLeast(uiState.speakers.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = uiState.onlyAbsentFilter,
                    onClick = viewModel::toggleOnlyAbsentFilter,
                    label = { Text("Tylko nieobecni") }
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(uiState.error ?: "Blad", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::loadSpeakers) { Text("Ponow") }
                        }
                    }
                }
                uiState.visibleSpeakers.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                uiState.searchQuery.isNotBlank() -> "Brak wynikow"
                                uiState.onlyAbsentFilter && uiState.attended > 0 -> "Wszyscy odhaczeni"
                                else -> "Brak prelegentow"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Text(
                        "${uiState.visibleSpeakers.size} prelegentow",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.visibleSpeakers, key = { it.speakerId }) { speaker ->
                            SpeakerCard(
                                speaker = speaker,
                                isCheckedIn = speaker.speakerId in uiState.checkedInSpeakerIds,
                                isPending = speaker.speakerId in uiState.pendingSpeakerIds,
                                onClick = { onSpeakerClick(speaker.speakerId) },
                                onCheckClick = {
                                    if (speaker.speakerId in uiState.checkedInSpeakerIds) {
                                        viewModel.requestUndoConfirm(speaker.speakerId)
                                    } else {
                                        viewModel.markAttended(speaker.speakerId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Undo confirm dialog
    val undoTarget = uiState.undoConfirmSpeakerId
    if (undoTarget != null) {
        val targetName = uiState.speakers.firstOrNull { it.speakerId == undoTarget }?.displayName.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::dismissUndoConfirm,
            title = { Text("Cofnac check-in?") },
            text = {
                Text(
                    if (targetName.isNotBlank())
                        "Cofnac check-in dla $targetName?"
                    else "Cofnac check-in?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.undoAttended(undoTarget) }) { Text("Cofnij") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUndoConfirm) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun SpeakerCard(
    speaker: Speaker,
    isCheckedIn: Boolean,
    isPending: Boolean,
    onClick: () -> Unit,
    onCheckClick: () -> Unit
) {
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
            // Avatar
            MdAsyncImage(
                model = speaker.photoUrl.ifBlank { null },
                contentDescription = speaker.displayName,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                initials = speaker.initials,
                shape = CircleShape,
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    speaker.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (speaker.organization.isNotBlank()) {
                    Text(
                        speaker.organization,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (speaker.affiliation.isNotBlank()) {
                    Text(
                        speaker.affiliation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right-side check toggle — WO-MOB-015
            if (isPending) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                Checkbox(
                    checked = isCheckedIn,
                    onCheckedChange = { onCheckClick() }
                )
            }
        }
    }
}
