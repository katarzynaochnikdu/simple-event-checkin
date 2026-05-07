package pl.medidesk.mobile.feature.participants.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.ui.components.SyncStatusBar
import pl.medidesk.mobile.core.ui.theme.ScanSuccess
import pl.medidesk.mobile.feature.participants.presentation.viewmodel.ParticipantsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsScreen(
    eventId: String,
    filterType: String?,
    ticketClassId: String?,
    onBackClick: () -> Unit = {},
    onParticipantClick: (Long) -> Unit,
    viewModel: ParticipantsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(filterType, ticketClassId) {
        when (filterType) {
            "checkedIn" -> viewModel.onFilterCheckedIn(true)
            "pending" -> viewModel.onFilterCheckedIn(false)
            else -> viewModel.onFilterCheckedIn(null)
        }
        viewModel.onFilterTicketClass(ticketClassId)
    }

    if (uiState.checkinDialogParticipant != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text("Zapis wejścia") },
            text = { Text("Czy chcesz zameldować uczestnika ${uiState.checkinDialogParticipant?.displayName}?") },
            confirmButton = {
                Button(onClick = { viewModel.performManualCheckin(uiState.checkinDialogParticipant!!) }) {
                    Text("Tak, odznacz")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) { Text("Anuluj") }
            }
        )
    }
    
    if (uiState.checkoutDialogParticipant != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialogs,
            title = { Text("Cofanie wejścia") },
            text = { Text("Czy na pewno chcesz cofnąć zameldowanie dla ${uiState.checkoutDialogParticipant?.displayName}?") },
            confirmButton = {
                Button(onClick = { viewModel.performManualCheckout(uiState.checkoutDialogParticipant!!) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Cofnij wejście")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialogs) { Text("Anuluj") }
            }
        )
    }

    val isFilterActive = uiState.filterCheckedIn != null || uiState.selectedTicketClassId != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Uczestnicy", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                uiState.participants.size.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {

            // Search Bar + Filter Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQuery,
                    placeholder = { Text("Imię, firma, bilet, płatnik, tag...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Spacer(Modifier.width(8.dp))

                // Filter icon with badge when active
                BadgedBox(
                    badge = {
                        if (isFilterActive) Badge(containerColor = MaterialTheme.colorScheme.error)
                    }
                ) {
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Filtry",
                            tint = if (isFilterActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh(eventId) },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.filteredParticipants, key = { "${it.id}_${it.isWalkin}" }) { participant ->
                        ParticipantItem(
                            participant = participant, 
                            onClick = { onParticipantClick(participant.id) },
                            onStatusClick = {
                                if (participant.isCheckedIn) {
                                    viewModel.showCheckoutDialog(participant)
                                } else {
                                    viewModel.showCheckinDialog(participant)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filtry", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isFilterActive) {
                        TextButton(onClick = {
                            viewModel.onFilterCheckedIn(null)
                            viewModel.onFilterTicketClass(null)
                        }) {
                            Text("Wyczyść", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Status wejścia", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Wszyscy", true to "Odznaczeni", false to "Oczekujący").forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.filterCheckedIn == value,
                            onClick = { viewModel.onFilterCheckedIn(value) },
                            label = { Text(label) },
                            colors = filterChipColors()
                        )
                    }
                }

                if (uiState.ticketClasses.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text("Typ biletu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

                    FilterChip(
                        selected = uiState.selectedTicketClassId == null,
                        onClick = { viewModel.onFilterTicketClass(null) },
                        label = { Text("Wszystkie") },
                        colors = filterChipColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    uiState.ticketClasses.forEach { ticketClass ->
                        FilterChip(
                            selected = uiState.selectedTicketClassId == ticketClass.ticketClassId,
                            onClick = { viewModel.onFilterTicketClass(ticketClass.ticketClassId) },
                            label = { Text(ticketClass.ticketName) },
                            colors = filterChipColors()
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
)



@Composable
private fun ParticipantItem(participant: Participant, onClick: () -> Unit, onStatusClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${participant.lastName.orEmpty()}, ${participant.firstName.orEmpty()}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (participant.tags.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = participant.tags.first(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                val companyText = participant.company.orEmpty().ifEmpty { 
                    participant.buyerName?.let { "Płatnik: $it" } ?: "Indywidualny uczestnik"
                }
                Text(
                    text = companyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (!participant.email.isNullOrEmpty() || !participant.phone.isNullOrEmpty()) {
                    val contactText = listOfNotNull(participant.phone, participant.email).joinToString(" • ")
                    Text(
                        text = contactText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
                }
            }
            

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = participant.ticketName ?: "Standard",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val orderStatus = participant.orderStatus
                if (!orderStatus.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val statusColor = when (orderStatus.lowercase()) {
                        "paid" -> Color(0xFFE8F5E9)
                        "unpaid" -> Color(0xFFFFEBEE)
                        "cancelled" -> Color(0xFFECEFF1)
                        else -> Color(0xFFFFF3E0)
                    }
                    val statusTextColor = when (orderStatus.lowercase()) {
                        "paid" -> Color(0xFF2E7D32)
                        "unpaid" -> Color(0xFFC62828)
                        "cancelled" -> Color(0xFF546E7A)
                        else -> Color(0xFFEF6C00)
                    }
                    val statusText = when (orderStatus.lowercase()) {
                        "paid" -> "Opłacone"
                        "unpaid" -> "Nieopłacone"
                        "cancelled" -> "Anulowane"
                        else -> orderStatus
                    }

                    Surface(
                        color = if (isSystemInDarkTheme()) statusTextColor.copy(alpha = 0.2f) else statusColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isSystemInDarkTheme()) statusTextColor.copy(alpha = 0.9f) else statusTextColor
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            IconButton(onClick = onStatusClick) {
                if (participant.isCheckedIn) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Odznaczony",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = "Oczekujący",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
