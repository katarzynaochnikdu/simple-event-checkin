package pl.medidesk.mobile.feature.participants.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.ui.components.LoadingScreen
import pl.medidesk.mobile.core.ui.theme.MdBlue
import pl.medidesk.mobile.core.ui.theme.StatusColors
import pl.medidesk.mobile.feature.participants.presentation.viewmodel.CheckinResult
import pl.medidesk.mobile.feature.participants.presentation.viewmodel.ParticipantDetailsUiState
import pl.medidesk.mobile.feature.participants.presentation.viewmodel.ParticipantDetailsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Aliasy do theme palette — patrz core-ui/theme/StatusColors.kt + Color.kt.
// Trzymamy lokalne nazwy żeby nie przepisywać wszystkich call-site'ów,
// ale pochodzą one z jednego źródła prawdy (theme), nie hardcode'ów.
private val StatusGreen = StatusColors.Paid
private val StatusAmber = StatusColors.Pending
private val StatusRed = StatusColors.Cancelled
private val StatusGray = StatusColors.Neutral
private val AccentBlue = MdBlue

@Composable
fun ParticipantDetailsScreen(
    participantId: Long,
    onBackClick: () -> Unit,
    viewModel: ParticipantDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val checkinResult by viewModel.checkinResult.collectAsStateWithLifecycle()

    // Snackbar dla błędów i potwierdzeń check-in
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(checkinResult) {
        when (val r = checkinResult) {
            is CheckinResult.Success -> {
                snackbarHostState.showSnackbar("✓ Check-in wykonany")
                viewModel.resetCheckinResult()
            }
            is CheckinResult.UndoSuccess -> {
                snackbarHostState.showSnackbar("↩ Check-in cofnięty")
                viewModel.resetCheckinResult()
            }
            is CheckinResult.AlreadyCheckedIn -> {
                snackbarHostState.showSnackbar("Uczestnik już jest odznaczony")
                viewModel.resetCheckinResult()
            }
            is CheckinResult.Failure -> {
                snackbarHostState.showSnackbar("Błąd: ${r.message}")
                viewModel.resetCheckinResult()
            }
            else -> Unit
        }
    }

    // Stany dialogów potwierdzenia
    var showCheckinDialog by remember { mutableStateOf(false) }
    var showUndoDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val participant = (uiState as? ParticipantDetailsUiState.Success)?.participant

    // Dialog — potwierdź Check-In
    if (showCheckinDialog) {
        AlertDialog(
            onDismissRequest = { showCheckinDialog = false },
            icon = { Icon(Icons.Default.MeetingRoom, null, tint = MdBlue) },
            title = { Text("Potwierdzenie Check-In", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Czy potwierdzasz wykonanie Check-In dla\n${participant?.displayName ?: "uczestnika"}?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showCheckinDialog = false; viewModel.performCheckin() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color.White)
                ) { Text("Tak, Check-In") }
            },
            dismissButton = {
                TextButton(onClick = { showCheckinDialog = false }) { Text("Anuluj") }
            }
        )
    }

    // Dialog — potwierdź odwołanie Check-In
    if (showUndoDialog) {
        AlertDialog(
            onDismissRequest = { showUndoDialog = false },
            icon = { Icon(Icons.Default.Undo, null, tint = StatusRed) },
            title = { Text("Odwołanie Check-In", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Czy na pewno chcesz cofnąć Check-In dla\n${participant?.displayName ?: "uczestnika"}?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showUndoDialog = false; viewModel.performUndoCheckin() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = Color.White)
                ) { Text("Tak, cofnij") }
            },
            dismissButton = {
                TextButton(onClick = { showUndoDialog = false }) { Text("Anuluj") }
            }
        )
    }

    // Bez Scaffold/TopBar — zamiast tego Box z treścią + IconButton "wróć" jako overlay w lewym
    // górnym rogu (na wysokości avatara) + SnackbarHost na dole. Brak wypełniaczy / sticky barów.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is ParticipantDetailsUiState.Loading -> LoadingScreen("Ładowanie...")
            is ParticipantDetailsUiState.Error -> Box(
                Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) { Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            is ParticipantDetailsUiState.Success -> ParticipantDetailsContent(
                participant = state.participant,
                scrollState = scrollState,
                checkinResult = checkinResult,
                onCheckinClick = { showCheckinDialog = true },
                onUndoClick = { showUndoDialog = true },
                onBackClick = onBackClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Snackbar overlay
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ParticipantDetailsContent(
    participant: Participant,
    scrollState: androidx.compose.foundation.ScrollState,
    checkinResult: CheckinResult,
    onCheckinClick: () -> Unit,
    onUndoClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
    ) {
        HeroHeader(participant, onBackClick)
        Spacer(Modifier.height(20.dp))
        StatusIconsRow(participant)
        Spacer(Modifier.height(16.dp))
        CheckinBanner(participant, checkinResult, onCheckinClick, onUndoClick)
        Spacer(Modifier.height(16.dp))
        ContactCard(participant, context)
        Spacer(Modifier.height(12.dp))
        OrderSection(participant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroHeader(participant: Participant, onBackClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme

    val initials = buildString {
        participant.firstName?.firstOrNull()?.let { append(it.uppercaseChar()) }
        participant.lastName?.firstOrNull()?.let { append(it.uppercaseChar()) }
    }.ifEmpty { "?" }

    // Górny "rząd" z avatarem — strzałka wstecz w lewym górnym rogu, NA TEJ SAMEJ wysokości co avatar.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Strzałka po lewej, zakotwiczona pionowo do środka avatara (avatar 72dp, IconButton 48dp)
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wróć",
                tint = cs.onBackground
            )
        }
        // Avatar wycentrowany
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
        }
    }

    // Reszta nagłówka (imię, firma, bilet, tagi) — wycentrowana
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(14.dp))

        Text(
            text = participant.displayName,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
            textAlign = TextAlign.Center
        )

        val identifier = participant.company?.takeIf { it.isNotBlank() }
            ?: participant.buyerName?.takeIf { it.isNotBlank() }
        if (identifier != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Business, null, modifier = Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(identifier, fontSize = 14.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!participant.ticketName.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = cs.primaryContainer
            ) {
                Text(
                    text = participant.ticketName!!,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer
                )
            }
        }

        if (participant.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                participant.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cs.surfaceVariant,
                        border = BorderStroke(1.dp, cs.outlineVariant)
                    ) {
                        Text(
                            tag,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusIconsRow(participant: Participant) {
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val attendanceRaw = participant.attendanceStatus?.lowercase() ?: ""
            val (rsvpIcon, rsvpColor) = when {
                attendanceRaw in listOf("attending", "confirmed", "rsvp_confirmed") ->
                    Icons.Default.EventAvailable to StatusGreen
                attendanceRaw in listOf("declined", "rsvp_declined", "cancelled") ->
                    Icons.Default.EventBusy to StatusRed
                attendanceRaw.isNotBlank() && attendanceRaw != "n/a" ->
                    Icons.Default.HelpOutline to StatusGray
                else -> Icons.Outlined.Event to cs.outlineVariant
            }
            StatusIcon(rsvpIcon, rsvpColor, "RSVP")

            val orderRaw = participant.orderStatus?.lowercase() ?: ""
            val (payIcon, payColor) = when {
                orderRaw in listOf("paid", "free") -> Icons.Default.Paid to StatusGreen
                orderRaw == "unpaid" -> Icons.Outlined.Description to StatusRed   // nieopłacone = proforma, czerwony
                orderRaw.contains("pending") -> Icons.Outlined.Description to StatusAmber  // oczekuje na płatność
                orderRaw in listOf("cancelled", "refunded") -> Icons.Default.MoneyOff to StatusRed
                orderRaw.contains("expired") -> Icons.Default.TimerOff to StatusGray
                orderRaw.isNotBlank() && orderRaw != "n/a" -> Icons.Outlined.Payments to StatusGray
                else -> Icons.Outlined.Payments to cs.outlineVariant
            }
            StatusIcon(payIcon, payColor, "Płatność")

            val (ciIcon, ciColor) = if (participant.isCheckedIn) {
                Icons.Default.CheckCircle to StatusGreen
            } else {
                Icons.Default.MeetingRoom to AccentBlue
            }
            StatusIcon(ciIcon, ciColor, "Check-In")
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CheckinBanner(
    participant: Participant,
    checkinResult: CheckinResult,
    onCheckinClick: () -> Unit,
    onUndoClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isCheckedIn = participant.isCheckedIn
    val isLoading = checkinResult is CheckinResult.Loading

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isCheckedIn) StatusGreen.copy(alpha = 0.08f) else cs.surface,
        border = BorderStroke(1.dp, if (isCheckedIn) StatusGreen.copy(alpha = 0.3f) else cs.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isCheckedIn) StatusGreen.copy(alpha = 0.12f) else cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCheckedIn) Icons.Default.CheckCircle else Icons.Default.MeetingRoom,
                    null,
                    tint = if (isCheckedIn) StatusGreen else cs.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isCheckedIn) "Check-In wykonany" else "Oczekujący na Check-In",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCheckedIn) StatusGreen else cs.onSurface
                )
                if (isCheckedIn && !participant.checkedInAt.isNullOrBlank()) {
                    Text(
                        formatDateTime(participant.checkedInAt!!),
                        fontSize = 12.sp,
                        color = StatusGreen.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isCheckedIn) {
                // Cofnij check-in — dla pomyłek
                OutlinedButton(
                    onClick = onUndoClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = StatusRed
                        )
                    } else {
                        Icon(Icons.Default.Undo, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cofnij", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Button(
                    onClick = onCheckinClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusGreen,
                        contentColor = Color.White,
                        disabledContainerColor = StatusGreen.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.MeetingRoom, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check-In", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCard(participant: Participant, context: android.content.Context) {
    val cs = MaterialTheme.colorScheme
    val hasEmail = !participant.email.isNullOrBlank()
    val hasPhone = !participant.phone.isNullOrBlank()
    if (!hasEmail && !hasPhone) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Column {
            if (hasEmail) {
                ContactRow(
                    icon = Icons.Outlined.Email,
                    label = participant.email!!,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${participant.email}")))
                    }
                )
            }
            if (hasEmail && hasPhone) {
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (hasPhone) {
                ContactRow(
                    icon = Icons.Outlined.Phone,
                    label = participant.phone!!,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${participant.phone}")))
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = cs.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = cs.onSurface, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OrderSection(participant: Participant) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val hasOrderDetails = !participant.eventOrderId.isNullOrBlank()
            || !participant.buyerName.isNullOrBlank()
            || !participant.buyerEmail.isNullOrBlank()
            || !participant.orderStatus.isNullOrBlank()

    if (!hasOrderDetails) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Receipt, null, modifier = Modifier.size(18.dp), tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Szczegóły zamówienia",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (!expanded) {
                    // Collapsed preview — pokazuj licznik osób z zamówienia (1/2 odznacz.) zamiast duplikatu statusu z ikonek powyżej
                    val total = participant.orderParticipantsTotal ?: 0
                    val checkedIn = participant.orderParticipantsCheckedIn ?: 0
                    if (total > 1) {
                        val pillColor = if (checkedIn >= total) StatusGreen else StatusAmber
                        Surface(shape = RoundedCornerShape(6.dp), color = pillColor.copy(alpha = 0.12f)) {
                            Text(
                                "$checkedIn/$total odzn.",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = pillColor
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }

                Icon(
                    Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    tint = cs.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(4.dp))

                    // 1) Płatnik — firma i osoba zamawiająca
                    val bName = participant.buyerName
                    val bEmail = participant.buyerEmail
                    val pCompany = participant.purchaserCompany
                    if (!pCompany.isNullOrBlank()) {
                        DetailRow(Icons.Outlined.Business, "Płatnik", pCompany)
                    } else if (!bName.isNullOrBlank()) {
                        DetailRow(Icons.Outlined.Person, "Płatnik", bName)
                    }
                    if (!bEmail.isNullOrBlank()) {
                        DetailRow(Icons.Outlined.AlternateEmail, "Email", bEmail)
                    }

                    // 2) Osoby z zamówienia
                    val total = participant.orderParticipantsTotal ?: 0
                    val checkedIn = participant.orderParticipantsCheckedIn ?: 0
                    if (total > 0) {
                        val pillColor = if (checkedIn >= total) StatusGreen else StatusAmber
                        DetailRowWithPill(
                            Icons.Outlined.Group,
                            "Osoby",
                            "$checkedIn / $total odznaczono",
                            pillColor
                        )
                    }

                    // 3) NIP
                    val pNip = participant.purchaserNip
                    if (!pNip.isNullOrBlank()) {
                        DetailRow(Icons.Outlined.Badge, "NIP", pNip)
                    }

                    // 4) Forma płatności
                    val (payIcon, payText) = paymentMethodDisplay(participant.paymentMethod, participant.orderStatus)
                    if (payText.isNotBlank()) {
                        DetailRow(payIcon, "Forma", payText)
                    }

                    // 5) Nr zamówienia
                    if (!participant.eventOrderId.isNullOrBlank()) {
                        DetailRow(Icons.Outlined.Tag, "Nr zamówienia", participant.eventOrderId!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
        Text(
            value, fontSize = 13.sp, color = cs.onSurface, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DetailRowWithPill(icon: ImageVector, label: String, value: String, pillColor: Color) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = cs.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(6.dp), color = pillColor.copy(alpha = 0.12f)) {
            Text(
                value,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pillColor
            )
        }
    }
}

/**
 * Mapuje payment_option_name (z backendu) na (ikona, czytelny tekst).
 * Heurystyka tekstowa — backend zwraca nazwy typu "BLIK", "Karta", "Przelew tradycyjny", "Faktura proforma".
 * orderStatus = "free" → FOC (Free of Charge), niezależnie od paymentMethod.
 */
private fun paymentMethodDisplay(raw: String?, orderStatus: String?): Pair<ImageVector, String> {
    if (orderStatus?.lowercase() == "free") {
        return Icons.Outlined.CardGiftcard to "FOC (bezpłatne)"
    }
    val txt = raw?.trim().orEmpty()
    if (txt.isBlank()) return Icons.Outlined.Payments to ""
    val low = txt.lowercase()
    val icon = when {
        "blik" in low -> Icons.Outlined.Smartphone
        "karta" in low || "card" in low -> Icons.Outlined.CreditCard
        "przelew" in low || "transfer" in low -> Icons.Outlined.AccountBalance
        "faktura" in low || "proforma" in low || "invoice" in low -> Icons.Outlined.Receipt
        "gotów" in low || "gotowk" in low || "cash" in low -> Icons.Outlined.Payments
        else -> Icons.Outlined.Payments
    }
    return icon to txt
}

private fun translateStatus(raw: String): Pair<String, Color> {
    return when (raw.replace(" ", "_")) {
        "paid" -> "Opłacone" to StatusGreen
        "free" -> "Bezpłatne" to StatusGreen
        "unpaid" -> "Nieopłacone" to StatusAmber
        "pending_payment" -> "Oczekuje na płatność" to StatusAmber
        "payment_expired" -> "Płatność wygasła" to StatusGray
        "cancelled" -> "Anulowane" to StatusRed
        "refunded" -> "Zwrócone" to StatusRed
        else -> if (raw.isNotBlank()) raw.replaceFirstChar { it.uppercaseChar() } to StatusGray else "" to StatusGray
    }
}

private fun translateRsvp(raw: String): Pair<String, Color> {
    return when (raw.replace(" ", "_")) {
        "attending", "confirmed", "rsvp_confirmed" -> "Potwierdzony" to StatusGreen
        "registered" -> "Zarejestrowany" to AccentBlue
        "declined", "rsvp_declined" -> "Odrzucony" to StatusRed
        "cancelled" -> "Anulowany" to StatusRed
        else -> if (raw.isNotBlank()) raw.replaceFirstChar { it.uppercaseChar() } to StatusGray else "" to StatusGray
    }
}

private fun formatDateTime(raw: String): String {
    if (raw.isBlank()) return "Brak danych"
    return try {
        val stripped = raw.replace(" ", "T")
        val dt = try {
            java.time.ZonedDateTime.parse(stripped).toLocalDateTime()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(stripped).toLocalDateTime()
            } catch (_: Exception) {
                val clean = if (stripped.length >= 19) stripped.substring(0, 19) else stripped
                LocalDateTime.parse(clean)
            }
        }
        dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
    } catch (_: Exception) { raw }
}
