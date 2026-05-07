package pl.medidesk.mobile.feature.participants.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.ui.components.LoadingScreen
import pl.medidesk.mobile.feature.participants.presentation.viewmodel.ParticipantDetailsUiState
import pl.medidesk.mobile.feature.participants.presentation.viewmodel.ParticipantDetailsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val LabelColor = Color(0xFF94A3B8)
private val ValueColor = Color(0xFFE2E8F0)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentGreen = Color(0xFF22C55E)
private val AccentAmber = Color(0xFFF59E0B)
private val AccentRed = Color(0xFFEF4444)
private val AccentGray = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantDetailsScreen(
    participantId: Long,
    onBackClick: () -> Unit,
    viewModel: ParticipantDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is ParticipantDetailsUiState.Loading -> LoadingScreen("Ładowanie...")
            is ParticipantDetailsUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(state.message, color = LabelColor) }
            is ParticipantDetailsUiState.Success -> ParticipantDetailsContent(
                participant = state.participant,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ParticipantDetailsContent(participant: Participant, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero header
        HeroHeader(participant)

        Spacer(Modifier.height(20.dp))

        // Status indicators row (icons like desktop)
        StatusIconsRow(participant)

        Spacer(Modifier.height(16.dp))

        // Check-in banner
        CheckinBanner(participant)

        Spacer(Modifier.height(16.dp))

        // Contact
        ContactCard(participant, context)

        Spacer(Modifier.height(12.dp))

        // Order details (expandable)
        OrderSection(participant)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroHeader(participant: Participant) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        val initials = buildString {
            participant.firstName?.firstOrNull()?.let { append(it.uppercaseChar()) }
            participant.lastName?.firstOrNull()?.let { append(it.uppercaseChar()) }
        }.ifEmpty { "?" }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AccentBlue, Color(0xFF8B5CF6)))),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(14.dp))

        // Name
        Text(
            text = participant.displayName,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // Company / buyer name as identifier
        val identifier = participant.company?.takeIf { it.isNotBlank() }
            ?: participant.buyerName?.takeIf { it.isNotBlank() }
        if (identifier != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Business, null, modifier = Modifier.size(14.dp), tint = LabelColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = identifier,
                    fontSize = 14.sp,
                    color = LabelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Ticket badge
        if (!participant.ticketName.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AccentBlue.copy(alpha = 0.15f)
            ) {
                Text(
                    text = participant.ticketName!!,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
            }
        }

        // Tags
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
                        color = CardBg,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            tag,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = LabelColor,
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // RSVP status
            val attendanceRaw = participant.attendanceStatus?.lowercase() ?: ""
            val (rsvpIcon, rsvpColor, rsvpLabel) = when {
                attendanceRaw in listOf("attending", "confirmed", "rsvp_confirmed") ->
                    Triple(Icons.Default.EventAvailable, AccentGreen, "RSVP")
                attendanceRaw in listOf("declined", "rsvp_declined") ->
                    Triple(Icons.Default.EventBusy, AccentRed, "RSVP")
                attendanceRaw == "cancelled" ->
                    Triple(Icons.Default.Cancel, AccentRed, "RSVP")
                attendanceRaw.isNotBlank() && attendanceRaw != "n/a" ->
                    Triple(Icons.Default.HelpOutline, AccentGray, "RSVP")
                else -> Triple(Icons.Outlined.Event, CardBorder, "RSVP")
            }
            StatusIcon(rsvpIcon, rsvpColor, rsvpLabel)

            // Payment status
            val orderRaw = participant.orderStatus?.lowercase() ?: ""
            val (payIcon, payColor, payLabel) = when {
                orderRaw == "paid" || orderRaw == "free" ->
                    Triple(Icons.Default.Paid, AccentGreen, "Płatność")
                orderRaw == "unpaid" || orderRaw.contains("pending") ->
                    Triple(Icons.Outlined.Payments, AccentAmber, "Płatność")
                orderRaw in listOf("cancelled", "refunded") ->
                    Triple(Icons.Default.MoneyOff, AccentRed, "Płatność")
                orderRaw.contains("expired") ->
                    Triple(Icons.Default.TimerOff, AccentGray, "Płatność")
                orderRaw.isNotBlank() && orderRaw != "n/a" ->
                    Triple(Icons.Outlined.Payments, AccentGray, "Płatność")
                else -> Triple(Icons.Outlined.Payments, CardBorder, "Płatność")
            }
            StatusIcon(payIcon, payColor, payLabel)

            // Check-in status
            val (ciIcon, ciColor, ciLabel) = if (participant.isCheckedIn) {
                Triple(Icons.Default.CheckCircle, AccentGreen, "Wejście")
            } else {
                Triple(Icons.Outlined.Schedule, AccentGray, "Wejście")
            }
            StatusIcon(ciIcon, ciColor, ciLabel)
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = LabelColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CheckinBanner(participant: Participant) {
    val isCheckedIn = participant.isCheckedIn

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isCheckedIn) AccentGreen.copy(alpha = 0.1f) else CardBg,
        border = BorderStroke(1.dp, if (isCheckedIn) AccentGreen.copy(alpha = 0.3f) else CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isCheckedIn) AccentGreen.copy(alpha = 0.15f) else Color(0xFF374151)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCheckedIn) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    null,
                    tint = if (isCheckedIn) AccentGreen else LabelColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (isCheckedIn) "Zameldowany" else "Oczekujący na wejście",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCheckedIn) AccentGreen else ValueColor
                )
                if (isCheckedIn && !participant.checkedInAt.isNullOrBlank()) {
                    Text(
                        formatDateTime(participant.checkedInAt!!),
                        fontSize = 12.sp,
                        color = if (isCheckedIn) AccentGreen.copy(alpha = 0.7f) else LabelColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactCard(participant: Participant, context: android.content.Context) {
    val hasEmail = !participant.email.isNullOrBlank()
    val hasPhone = !participant.phone.isNullOrBlank()
    if (!hasEmail && !hasPhone) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
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
                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
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
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = AccentBlue)
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = ValueColor, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = LabelColor.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OrderSection(participant: Participant) {
    var expanded by remember { mutableStateOf(false) }
    val hasOrderDetails = !participant.eventOrderId.isNullOrBlank()
            || !participant.buyerName.isNullOrBlank()
            || !participant.buyerEmail.isNullOrBlank()
            || !participant.orderStatus.isNullOrBlank()

    if (!hasOrderDetails) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            // Header — always visible, clickable to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Receipt, null, modifier = Modifier.size(18.dp), tint = AccentBlue)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Szczegóły zamówienia",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ValueColor,
                    modifier = Modifier.weight(1f)
                )

                // Summary pills when collapsed
                if (!expanded) {
                    val orderRaw = participant.orderStatus?.lowercase() ?: ""
                    val (statusText, statusColor) = translateStatus(orderRaw)
                    if (statusText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                statusText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }

                Icon(
                    Icons.Default.ExpandMore,
                    null,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    tint = LabelColor
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(4.dp))

                    // Order ID
                    if (!participant.eventOrderId.isNullOrBlank()) {
                        DetailRow(Icons.Outlined.Tag, "Zamówienie", participant.eventOrderId!!)
                    }

                    // Payment status
                    val orderRaw = participant.orderStatus?.lowercase() ?: ""
                    if (orderRaw.isNotBlank() && orderRaw != "n/a") {
                        val (statusText, statusColor) = translateStatus(orderRaw)
                        DetailRowWithPill(Icons.Outlined.Payments, "Płatność", statusText, statusColor)
                    }

                    // RSVP
                    val rsvpRaw = participant.attendanceStatus?.lowercase() ?: ""
                    if (rsvpRaw.isNotBlank() && rsvpRaw != "n/a") {
                        val (rsvpText, rsvpColor) = translateRsvp(rsvpRaw)
                        DetailRowWithPill(Icons.Outlined.EventAvailable, "RSVP", rsvpText, rsvpColor)
                    }

                    // Buyer section
                    val bName = participant.buyerName
                    val bEmail = participant.buyerEmail
                    val hasBuyer = !bName.isNullOrBlank() || !bEmail.isNullOrBlank()
                    if (hasBuyer) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = CardBorder.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "PŁATNIK",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LabelColor.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                        if (!bName.isNullOrBlank()) {
                            DetailRow(Icons.Outlined.Person, "Nazwa", bName)
                        }
                        if (!bEmail.isNullOrBlank()) {
                            DetailRow(Icons.Outlined.AlternateEmail, "Email", bEmail)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = LabelColor.copy(alpha = 0.6f))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, color = LabelColor, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = ValueColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DetailRowWithPill(icon: ImageVector, label: String, value: String, pillColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = LabelColor.copy(alpha = 0.6f))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, color = LabelColor, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = pillColor.copy(alpha = 0.12f)
        ) {
            Text(
                value,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = pillColor
            )
        }
    }
}

private fun translateStatus(raw: String): Pair<String, Color> {
    return when (raw.replace(" ", "_")) {
        "paid" -> "Opłacone" to AccentGreen
        "free" -> "Bezpłatne" to AccentGreen
        "unpaid" -> "Nieopłacone" to AccentAmber
        "pending_payment" -> "Oczekuje na płatność" to AccentAmber
        "payment_expired" -> "Płatność wygasła" to AccentGray
        "cancelled" -> "Anulowane" to AccentRed
        "refunded" -> "Zwrócone" to AccentRed
        else -> if (raw.isNotBlank()) raw.replaceFirstChar { it.uppercaseChar() } to AccentGray else "" to AccentGray
    }
}

private fun translateRsvp(raw: String): Pair<String, Color> {
    return when (raw.replace(" ", "_")) {
        "attending", "confirmed", "rsvp_confirmed" -> "Potwierdzony" to AccentGreen
        "registered" -> "Zarejestrowany" to AccentBlue
        "declined", "rsvp_declined" -> "Odrzucony" to AccentRed
        "cancelled" -> "Anulowany" to AccentRed
        else -> if (raw.isNotBlank()) raw.replaceFirstChar { it.uppercaseChar() } to AccentGray else "" to AccentGray
    }
}

private fun formatDateTime(raw: String): String {
    if (raw.isBlank()) return "Brak danych"
    return try {
        val cleanRaw = if (raw.length >= 19) raw.substring(0, 19).replace(" ", "T") else raw
        val dt = LocalDateTime.parse(cleanRaw)
        dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
    } catch (e: Exception) { raw }
}
