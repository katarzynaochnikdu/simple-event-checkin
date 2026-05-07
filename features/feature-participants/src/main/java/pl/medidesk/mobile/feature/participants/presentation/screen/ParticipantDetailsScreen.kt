package pl.medidesk.mobile.feature.participants.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantDetailsScreen(
    participantId: Long,
    onBackClick: () -> Unit,
    viewModel: ParticipantDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Karta uczestnika", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = Color(0xFFF8F9FA)) {
            when (val state = uiState) {
                is ParticipantDetailsUiState.Loading -> LoadingScreen("Ładowanie...")
                is ParticipantDetailsUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.message) }
                is ParticipantDetailsUiState.Success -> ParticipantDetailsContent(state.participant)
            }
        }
    }
}

@Composable
private fun ParticipantDetailsContent(participant: Participant) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Główny nagłówek (Imię, Nazwisko, Bilet, Firma) - bez ramki, czysty tekst na tłe
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(
                    text = participant.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF111827) // Bardzo ciemny szary / czarny
                )
                
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!participant.ticketName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEEF2FF)
                        ) {
                            Text(
                                text = participant.ticketName!!,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF3730A3),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    val companyLine = participant.company?.takeIf { it.isNotBlank() } ?: participant.buyerName?.takeIf { it.isNotBlank() }
                    if (companyLine != null) {
                        Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF6B7280))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = companyLine,
                            color = Color(0xFF4B5563),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (participant.tags.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        participant.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                            ) {
                                Text(
                                    tag, 
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), 
                                    fontSize = 12.sp, 
                                    color = Color(0xFF4B5563),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status wejścia - elegancki baner
        item {
            val isCheckedIn = participant.isCheckedIn
            val bgColor = if (isCheckedIn) Color(0xFFF0FDF4) else Color.White
            val borderColor = if (isCheckedIn) Color(0xFFBBF7D0) else Color(0xFFE5E7EB)
            val iconColor = if (isCheckedIn) Color(0xFF16A34A) else Color(0xFF9CA3AF)
            val textColor = if (isCheckedIn) Color(0xFF166534) else Color(0xFF374151)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = bgColor,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isCheckedIn) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (isCheckedIn) "Zameldowany" else "Oczekujący na wejście",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textColor
                        )
                        if (isCheckedIn && !participant.checkedInAt.isNullOrBlank()) {
                            Text("Data wejścia: ${formatDateTime(participant.checkedInAt!!)}", fontSize = 13.sp, color = textColor.copy(alpha=0.8f))
                        }
                    }
                }
            }
        }

        // Dane kontaktowe - klikalne rzędy grupowane w jednej karcie dla czystości
        item {
            SectionTitle("KONTAKT")
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFF3F4F6))
            ) {
                Column {
                    val hasEmail = !participant.email.isNullOrBlank()
                    val hasPhone = !participant.phone.isNullOrBlank()

                    if (hasEmail) {
                        val email = participant.email!!
                        ClickableRow(
                            icon = Icons.Default.Email,
                            label = "E-mail",
                            value = email,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                context.startActivity(intent)
                            }
                        )
                    }
                    if (hasPhone) {
                        val phone = participant.phone!!
                        if (hasEmail) HorizontalDivider(color = Color(0xFFF9FAFB), modifier = Modifier.padding(horizontal = 16.dp))
                        ClickableRow(
                            icon = Icons.Default.Phone,
                            label = "Telefon",
                            value = phone,
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            }
                        )
                    }
                    
                    if (!hasEmail && !hasPhone) {
                        Text(
                            "Brak danych kontaktowych", 
                            modifier = Modifier.padding(16.dp), 
                            color = Color(0xFF9CA3AF), 
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Rejestracja i Płatnik
        item {
            SectionTitle("SZCZEGÓŁY ZAMÓWIENIA")
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFF3F4F6))
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    InfoRowItem(label = "Status systemowy", value = participant.status.orEmpty().uppercase())
                    
                    val attendanceRaw = participant.attendanceStatus?.lowercase()
                    if (attendanceRaw != null && attendanceRaw.isNotBlank() && attendanceRaw != "n/a") {
                        val attText = when (attendanceRaw) {
                            "attending" -> "Potwierdzono (RSVP)"
                            "declined", "rsvp_declined" -> "Odrzucono (RSVP)"
                            "cancelled" -> "Anulowane"
                            else -> attendanceRaw.uppercase()
                        }
                        val attBg = when (attendanceRaw) {
                            "attending" -> Color(0xFFE0F2FE)
                            "declined", "rsvp_declined", "cancelled" -> Color(0xFFF3F4F6)
                            else -> Color(0xFFF3F4F6)
                        }
                        val attTextCol = when (attendanceRaw) {
                            "attending" -> Color(0xFF0284C7)
                            "declined", "rsvp_declined", "cancelled" -> Color(0xFF4B5563)
                            else -> Color(0xFF4B5563)
                        }
                        InfoRowPillItem(label = "Obecność (RSVP)", value = attText, bgColor = attBg, textColor = attTextCol)
                    }

                    val orderRaw = participant.orderStatus?.lowercase()
                    if (orderRaw != null && orderRaw.isNotBlank() && orderRaw != "n/a") {
                        val ordText = when (orderRaw) {
                            "paid" -> "Opłacone"
                            "unpaid" -> "Nieopłacone"
                            "cancelled", "refunded" -> "Anulowane"
                            else -> orderRaw.uppercase()
                        }
                        val ordBg = when (orderRaw) {
                            "paid" -> Color(0xFFDCFCE7)
                            "unpaid" -> Color(0xFFFEE2E2)
                            "cancelled", "refunded" -> Color(0xFFF3F4F6)
                            else -> Color(0xFFF3F4F6)
                        }
                        val ordTextCol = when (orderRaw) {
                            "paid" -> Color(0xFF166534)
                            "unpaid" -> Color(0xFF991B1B)
                            "cancelled", "refunded" -> Color(0xFF4B5563)
                            else -> Color(0xFF4B5563)
                        }
                        InfoRowPillItem(label = "Płatność wejściówki", value = ordText, bgColor = ordBg, textColor = ordTextCol)
                    }

                    InfoRowItem(label = "ID zamówienia", value = participant.eventOrderId ?: "--")
                    
                    val bName = participant.buyerName
                    if (!bName.isNullOrBlank() && bName != participant.displayName) {
                        HorizontalDivider(color = Color(0xFFF9FAFB), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        Text(
                            "DANE PŁATNIKA", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color(0xFF9CA3AF),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        InfoRowItem(label = "Firma / Osoba", value = bName)
                        if (!participant.buyerEmail.isNullOrBlank()) {
                            InfoRowItem(label = "E-mail", value = participant.buyerEmail!!)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF9CA3AF),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun ClickableRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF3F4F6),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp), tint = Color(0xFF4B5563))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.Medium)
                Text(value, fontSize = 15.sp, color = Color(0xFF111827), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun InfoRowPillItem(label: String, value: String, bgColor: Color, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@Composable
private fun InfoRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
        Text(value.ifEmpty { "--" }, fontSize = 14.sp, color = Color(0xFF111827), fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1.5f))
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
