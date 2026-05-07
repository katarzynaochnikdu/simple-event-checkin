package pl.medidesk.mobile.feature.participants.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
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

// Semantic status colors — same in light and dark
private val StatusGreen = Color(0xFF22C55E)
private val StatusAmber = Color(0xFFF59E0B)
private val StatusRed = Color(0xFFEF4444)
private val StatusGray = Color(0xFF64748B)
private val AccentBlue = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantDetailsScreen(
    participantId: Long,
    onBackClick: () -> Unit,
    viewModel: ParticipantDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
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
            ) { Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        HeroHeader(participant)
        Spacer(Modifier.height(20.dp))
        StatusIconsRow(participant)
        Spacer(Modifier.height(16.dp))
        CheckinBanner(participant)
        Spacer(Modifier.height(16.dp))
        ContactCard(participant, context)
        Spacer(Modifier.height(12.dp))
        OrderSection(participant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroHeader(participant: Participant) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val initials = buildString {
            participant.firstName?.firstOrNull()?.let { append(it.uppercaseChar()) }
            participant.lastName?.firstOrNull()?.let { append(it.uppercaseChar()) }
        }.ifEmpty { "?" }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(cs.primary, Color(0xFF8B5CF6)))),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
        }

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
                orderRaw == "unpaid" || orderRaw.contains("pending") -> Icons.Outlined.Payments to StatusAmber
                orderRaw in listOf("cancelled", "refunded") -> Icons.Default.MoneyOff to StatusRed
                orderRaw.contains("expired") -> Icons.Default.TimerOff to StatusGray
                orderRaw.isNotBlank() && orderRaw != "n/a" -> Icons.Outlined.Payments to StatusGray
                else -> Icons.Outlined.Payments to cs.outlineVariant
            }
            StatusIcon(payIcon, payColor, "Płatność")

            val (ciIcon, ciColor) = if (participant.isCheckedIn) {
                Icons.Default.CheckCircle to StatusGreen
            } else {
                Icons.Outlined.Schedule to StatusGray
            }
            StatusIcon(ciIcon, ciColor, "Wejście")
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
private fun CheckinBanner(participant: Participant) {
    val cs = MaterialTheme.colorScheme
    val isCheckedIn = participant.isCheckedIn

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isCheckedIn) StatusGreen.copy(alpha = 0.08f) else cs.surface,
        border = BorderStroke(1.dp, if (isCheckedIn) StatusGreen.copy(alpha = 0.3f) else cs.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isCheckedIn) StatusGreen.copy(alpha = 0.12f) else cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCheckedIn) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    null,
                    tint = if (isCheckedIn) StatusGreen else cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (isCheckedIn) "Zameldowany" else "Oczekujący na wejście",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCheckedIn) StatusGreen else cs.onSurface
                )
                if (isCheckedIn && !participant.checkedInAt.isNullOrBlank()) {
                    Text(
                        formatDateTime(participant.checkedInAt!!),
                        fontSize = 12.sp,
                        color = if (isCheckedIn) StatusGreen.copy(alpha = 0.7f) else cs.onSurfaceVariant
                    )
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
                    val orderRaw = participant.orderStatus?.lowercase() ?: ""
                    val (statusText, statusColor) = translateStatus(orderRaw)
                    if (statusText.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.12f)) {
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
                    Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    tint = cs.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(4.dp))

                    if (!participant.eventOrderId.isNullOrBlank()) {
                        DetailRow(Icons.Outlined.Tag, "Zamówienie", participant.eventOrderId!!)
                    }

                    val orderRaw = participant.orderStatus?.lowercase() ?: ""
                    if (orderRaw.isNotBlank() && orderRaw != "n/a") {
                        val (statusText, statusColor) = translateStatus(orderRaw)
                        DetailRowWithPill(Icons.Outlined.Payments, "Płatność", statusText, statusColor)
                    }

                    val rsvpRaw = participant.attendanceStatus?.lowercase() ?: ""
                    if (rsvpRaw.isNotBlank() && rsvpRaw != "n/a") {
                        val (rsvpText, rsvpColor) = translateRsvp(rsvpRaw)
                        DetailRowWithPill(Icons.Outlined.EventAvailable, "RSVP", rsvpText, rsvpColor)
                    }

                    val bName = participant.buyerName
                    val bEmail = participant.buyerEmail
                    if (!bName.isNullOrBlank() || !bEmail.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "PŁATNIK",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurfaceVariant.copy(alpha = 0.6f),
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
        val cleanRaw = if (raw.length >= 19) raw.substring(0, 19).replace(" ", "T") else raw
        val dt = LocalDateTime.parse(cleanRaw)
        dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
    } catch (e: Exception) { raw }
}
