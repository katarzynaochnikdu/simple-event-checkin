package pl.medidesk.mobile.feature.participants.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
        // Header hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
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
                    .background(
                        Brush.linearGradient(listOf(AccentBlue, Color(0xFF8B5CF6)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = participant.displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Ticket badge + company
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                val company = participant.company?.takeIf { it.isNotBlank() }
                    ?: participant.buyerName?.takeIf { it.isNotBlank() }
                if (company != null) {
                    if (!participant.ticketName.isNullOrBlank()) {
                        Text("  ·  ", color = LabelColor, fontSize = 14.sp)
                    }
                    Text(
                        text = company,
                        fontSize = 13.sp,
                        color = LabelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Tags
            if (participant.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }
            }
        }

        // Status banner
        StatusBanner(participant)

        Spacer(Modifier.height(16.dp))

        // Contact card
        ContactCard(participant, context)

        Spacer(Modifier.height(12.dp))

        // Order details card
        OrderDetailsCard(participant)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusBanner(participant: Participant) {
    val isCheckedIn = participant.isCheckedIn

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isCheckedIn) AccentGreen.copy(alpha = 0.1f) else CardBg,
        border = BorderStroke(
            1.dp,
            if (isCheckedIn) AccentGreen.copy(alpha = 0.3f) else CardBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCheckedIn) AccentGreen.copy(alpha = 0.15f)
                        else Color(0xFF374151)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCheckedIn) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (isCheckedIn) AccentGreen else LabelColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (isCheckedIn) "Zameldowany" else "Oczekujący na wejście",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${participant.email}"))
                        )
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
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${participant.phone}"))
                        )
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = AccentBlue)
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = ValueColor,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = LabelColor.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun OrderDetailsCard(participant: Participant) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Attendance RSVP
            val attendanceRaw = participant.attendanceStatus?.lowercase()
            if (attendanceRaw != null && attendanceRaw.isNotBlank() && attendanceRaw != "n/a") {
                val (attText, attColor) = when (attendanceRaw) {
                    "attending" -> "Potwierdzony" to AccentGreen
                    "confirmed", "rsvp_confirmed" -> "Potwierdzony" to AccentGreen
                    "declined", "rsvp_declined" -> "Odrzucony" to AccentRed
                    "cancelled" -> "Anulowany" to AccentRed
                    else -> attendanceRaw.replaceFirstChar { it.uppercaseChar() } to LabelColor
                }
                DetailRow(label = "RSVP", value = attText, valueColor = attColor)
            }

            // Order status / payment
            val orderRaw = participant.orderStatus?.lowercase()
            if (orderRaw != null && orderRaw.isNotBlank() && orderRaw != "n/a") {
                val (ordText, ordColor) = when (orderRaw) {
                    "paid" -> "Opłacone" to AccentGreen
                    "unpaid" -> "Nieopłacone" to AccentAmber
                    "cancelled", "refunded" -> "Anulowane" to AccentRed
                    else -> orderRaw.replaceFirstChar { it.uppercaseChar() } to LabelColor
                }
                DetailRow(label = "Płatność", value = ordText, valueColor = ordColor)
            }

            // Order ID
            if (!participant.eventOrderId.isNullOrBlank()) {
                DetailRow(label = "Zamówienie", value = participant.eventOrderId!!)
            }

            // Buyer info (only if different from participant)
            val bName = participant.buyerName
            if (!bName.isNullOrBlank() && bName != participant.displayName) {
                HorizontalDivider(
                    color = CardBorder.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                DetailRow(label = "Płatnik", value = bName)
                if (!participant.buyerEmail.isNullOrBlank()) {
                    DetailRow(label = "Email płatnika", value = participant.buyerEmail!!)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = ValueColor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = LabelColor,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
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
