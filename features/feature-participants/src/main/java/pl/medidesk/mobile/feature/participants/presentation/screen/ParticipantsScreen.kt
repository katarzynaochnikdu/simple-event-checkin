package pl.medidesk.mobile.feature.participants.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Check-in status icon (left side)
            IconButton(
                onClick = onStatusClick,
                modifier = Modifier.size(36.dp)
            ) {
                if (participant.isCheckedIn) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Zameldowany",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = "Oczekujący",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Name + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = participant.displayName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                val subtitle = participant.company?.takeIf { it.isNotBlank() }
                    ?: participant.email?.takeIf { it.isNotBlank() }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right side: ticket name + payment status
            Column(horizontalAlignment = Alignment.End) {
                if (!participant.ticketName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = participant.ticketName!!,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val orderStatus = participant.orderStatus
                if (!orderStatus.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    val (statusText, statusColor) = translateOrderStatus(orderStatus)
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

private fun translateOrderStatus(raw: String): Pair<String, Color> {
    return when (raw.lowercase().replace(" ", "_")) {
        "paid" -> "Opłacone" to Color(0xFF2E7D32)
        "unpaid" -> "Nieopłacone" to Color(0xFFC62828)
        "pending_payment" -> "Oczekuje" to Color(0xFFEF6C00)
        "payment_expired" -> "Wygasło" to Color(0xFF78909C)
        "cancelled" -> "Anulowane" to Color(0xFF546E7A)
        "refunded" -> "Zwrot" to Color(0xFF546E7A)
        "free" -> "Bezpłatne" to Color(0xFF2E7D32)
        else -> raw to Color(0xFF78909C)
    }
}
