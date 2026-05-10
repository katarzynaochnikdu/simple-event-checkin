package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.CheckinRequest
import pl.medidesk.mobile.core.network.dto.DeleteCompanyAssignmentRequest
import pl.medidesk.mobile.core.network.dto.MenteeDto
import java.time.Instant
import javax.inject.Inject

// ─── Domain models ────────────────────────────────────────────────────────────

data class CompanyGroup(
    val companyName: String,
    val shortName: String,
    val isPartner: Boolean,
    val crmAccountId: Long?,
    val count: Int,
    val paidCount: Int,
    val freeCount: Int,
    val pendingCount: Int,
    val participants: List<MenteeDto>
)

data class MyMenteesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val companies: List<CompanyGroup> = emptyList(),
    val withdrawingCompany: String? = null,
    val toastMessage: String? = null,
    val checkingInParticipantId: Long? = null,
    val pendingCheckInMentee: MenteeDto? = null
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MyMenteesViewModel @Inject constructor(
    private val api: MobileApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyMenteesUiState())
    val uiState = _uiState.asStateFlow()

    fun load(eventId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.getMyMentees(eventId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val companies = groupAndSort(body.data)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            companies = companies
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Błąd: ${response.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Nieznany błąd"
                    )
                }
            }
        }
    }

    fun withdrawGuardianship(eventId: String, companyName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(withdrawingCompany = companyName) }
            try {
                val response = api.deleteCompanyAssignment(
                    eventId = eventId,
                    body = DeleteCompanyAssignmentRequest(companyName = companyName)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update {
                        it.copy(
                            withdrawingCompany = null,
                            toastMessage = "Wycofano opiekę nad firmą \"$companyName\""
                        )
                    }
                    // reload listy po sukcesie
                    load(eventId)
                } else {
                    val errMsg = response.body()?.error ?: "Błąd: ${response.code()}"
                    _uiState.update {
                        it.copy(
                            withdrawingCompany = null,
                            toastMessage = "Nie udało się wycofać opieki: $errMsg"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        withdrawingCompany = null,
                        toastMessage = "Nie udało się wycofać opieki: ${e.message ?: "nieznany błąd"}"
                    )
                }
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun requestCheckIn(mentee: MenteeDto) {
        _uiState.update { it.copy(pendingCheckInMentee = mentee) }
    }

    fun cancelCheckIn() {
        _uiState.update { it.copy(pendingCheckInMentee = null) }
    }

    fun confirmCheckIn(eventId: String) {
        val mentee = _uiState.value.pendingCheckInMentee ?: return
        val ticketIdent = mentee.ticketNumber
            ?: mentee.ticketId
            ?: mentee.backstageTicketId
        if (ticketIdent.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    pendingCheckInMentee = null,
                    toastMessage = "Brak identyfikatora biletu — nie można wykonać check-in"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    checkingInParticipantId = mentee.participantId,
                    pendingCheckInMentee = null
                )
            }
            try {
                val response = api.checkin(
                    CheckinRequest(
                        ticketId = ticketIdent,
                        eventId = eventId,
                        scannedAt = Instant.now().toString()
                    )
                )
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    val name = "${mentee.firstName ?: ""} ${mentee.lastName ?: ""}".trim()
                    val msg = if (body.alreadyCheckedIn) {
                        if (name.isNotEmpty()) "$name już zarejestrowany" else "Już zarejestrowany"
                    } else {
                        if (name.isNotEmpty()) "Zarejestrowano przybycie: $name" else "Zarejestrowano przybycie"
                    }
                    _uiState.update {
                        it.copy(checkingInParticipantId = null, toastMessage = msg)
                    }
                    load(eventId)
                } else {
                    _uiState.update {
                        it.copy(
                            checkingInParticipantId = null,
                            toastMessage = body?.error ?: "Błąd check-in: ${response.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        checkingInParticipantId = null,
                        toastMessage = e.message ?: "Błąd check-in"
                    )
                }
            }
        }
    }

    private fun groupAndSort(mentees: List<MenteeDto>): List<CompanyGroup> {
        // Grupowanie po companyName (już dostarczone przez backend)
        val grouped = mentees
            .filter { !it.companyName.isNullOrBlank() }
            .groupBy { it.companyName!! }

        val result = grouped.map { (companyName, members) ->
            // Dedupe po crmPersonId ?: participantId
            val deduped = members.distinctBy { it.crmPersonId ?: it.participantId }

            var paid = 0
            var free = 0
            var pending = 0
            for (m in deduped) {
                val isFree = m.paymentType == 0 || (m.orderTotal ?: 0.0) == 0.0
                val isPaid = !isFree && m.orderStatus == "paid"
                val isPending = !isFree && !isPaid
                when {
                    isFree -> free++
                    isPaid -> paid++
                    isPending -> pending++
                }
            }

            // shortName / isPartner / crmAccountId — bierzemy z pierwszego (są wspólne per firma)
            val first = deduped.first()
            CompanyGroup(
                companyName = companyName,
                shortName = first.companyShortName.orEmpty(),
                isPartner = first.isPartner,
                crmAccountId = first.crmAccountId,
                count = deduped.size,
                paidCount = paid,
                freeCount = free,
                pendingCount = pending,
                participants = deduped
            )
        }

        return result.sortedWith(
            compareByDescending<CompanyGroup> { it.count }
                .thenBy { it.companyName.lowercase() }
        )
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMenteesScreen(
    eventId: String,
    onBackClick: () -> Unit,
    onParticipantClick: (Long) -> Unit,
    viewModel: MyMenteesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(eventId) { viewModel.load(eventId) }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moi podopieczni", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF152C5B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                actions = {
                    if (state.companies.isNotEmpty()) {
                        val totalPeople = state.companies.sumOf { it.count }
                        Text(
                            "${state.companies.size} firm · $totalPeople os.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF00897B)
                    )
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "", color = Color.Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.load(eventId) }) {
                            Text("Ponów")
                        }
                    }
                }

                state.companies.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.SupervisorAccount,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Brak podopiecznych",
                            color = Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Nie masz przypisanych uczestników do tego wydarzenia",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.companies, key = { it.companyName }) { group ->
                            CompanyCard(
                                group = group,
                                isWithdrawing = state.withdrawingCompany == group.companyName,
                                checkingInParticipantId = state.checkingInParticipantId,
                                onWithdraw = {
                                    viewModel.withdrawGuardianship(eventId, group.companyName)
                                },
                                onParticipantClick = onParticipantClick,
                                onCheckInRequest = { mentee -> viewModel.requestCheckIn(mentee) }
                            )
                        }
                    }
                }
            }

            state.pendingCheckInMentee?.let { mentee ->
                CheckInConfirmDialog(
                    mentee = mentee,
                    onConfirm = { viewModel.confirmCheckIn(eventId) },
                    onDismiss = { viewModel.cancelCheckIn() }
                )
            }
        }
    }
}

// ─── Company Card ─────────────────────────────────────────────────────────────

@Composable
private fun CompanyCard(
    group: CompanyGroup,
    isWithdrawing: Boolean,
    checkingInParticipantId: Long?,
    onWithdraw: () -> Unit,
    onParticipantClick: (Long) -> Unit,
    onCheckInRequest: (MenteeDto) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                    modifier = Modifier.size(24.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.companyName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1A1C1E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (group.isPartner) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF152C5B).copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "PARTNER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF152C5B)
                                )
                            }
                        }
                    }
                    if (group.shortName.isNotBlank()) {
                        Text(
                            group.shortName,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Gray.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${group.count} os.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        StatusBadge(group)
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { showWithdrawDialog = true },
                    enabled = !isWithdrawing
                ) {
                    Icon(
                        Icons.Default.RemoveModerator,
                        contentDescription = "Wycofaj opiekę",
                        tint = Color(0xFFFFB300)
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                group.participants.forEach { participant ->
                    ParticipantRow(
                        participant = participant,
                        isCheckingIn = checkingInParticipantId == participant.participantId,
                        onParticipantClick = onParticipantClick,
                        onCheckInClick = { onCheckInRequest(participant) }
                    )
                }
            }
        }
    }

    if (showWithdrawDialog) {
        WithdrawGuardianshipDialog(
            companyName = group.companyName,
            isPending = isWithdrawing,
            onConfirm = {
                onWithdraw()
                showWithdrawDialog = false
            },
            onDismiss = { showWithdrawDialog = false }
        )
    }
}

@Composable
private fun StatusBadge(group: CompanyGroup) {
    when {
        group.pendingCount > 0 -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFF8E1))
                    .border(1.dp, Color(0xFFFFB74D), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "OCZEKUJE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF57C00)
                )
            }
        }
        group.freeCount == group.count -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE2E8F0)) // slate-200
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "FREE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569) // slate-600
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE8F5E9))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "OPŁACONE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

// ─── Participant Row ──────────────────────────────────────────────────────────

@Composable
private fun ParticipantRow(
    participant: MenteeDto,
    isCheckingIn: Boolean,
    onParticipantClick: (Long) -> Unit,
    onCheckInClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onParticipantClick(participant.participantId) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(32.dp)) // wcięcie pod chevron
        Column(modifier = Modifier.weight(1f)) {
            val displayName = "${participant.firstName ?: ""} ${participant.lastName ?: ""}"
                .trim()
                .ifEmpty { "—" }
            Text(
                displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1C1E)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val phoneEmpty = participant.phone.isNullOrBlank()
                val emailEmpty = participant.email.isNullOrBlank()
                if (!phoneEmpty) {
                    Text(participant.phone!!, fontSize = 12.sp, color = Color.Gray)
                }
                if (!phoneEmpty && !emailEmpty) {
                    Text("·", fontSize = 12.sp, color = Color.Gray)
                }
                if (!emailEmpty) {
                    Text(
                        participant.email!!,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .widthIn(max = 100.dp)
            ) {
                Text(
                    (participant.ticketName ?: "Standard").uppercase(),
                    fontSize = 9.sp,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            when {
                participant.checkedIn -> {
                    Icon(
                        Icons.Default.HowToReg,
                        contentDescription = "Zarejestrowany",
                        tint = Color(0xFF00897B),
                        modifier = Modifier.size(20.dp)
                    )
                }
                isCheckingIn -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00897B)
                    )
                }
                else -> {
                    IconButton(
                        onClick = onCheckInClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00897B).copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.Default.HowToReg,
                            contentDescription = "Potwierdź przybycie",
                            tint = Color(0xFF00897B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Withdraw Dialog ──────────────────────────────────────────────────────────

@Composable
private fun WithdrawGuardianshipDialog(
    companyName: String,
    isPending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isPending) onDismiss() },
        title = { Text("Wycofać opiekę nad firmą?") },
        text = {
            Text(
                "Po wycofaniu opieki firma \"$companyName\" przestanie być widoczna na Twojej liście podopiecznych. Inny opiekun może zostać przypisany przez admina."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isPending,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE65100))
            ) {
                Text(if (isPending) "Wycofywanie…" else "Tak, wycofaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPending) {
                Text("Anuluj")
            }
        }
    )
}

// ─── Check-in Confirm Dialog ──────────────────────────────────────────────────

@Composable
private fun CheckInConfirmDialog(
    mentee: MenteeDto,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = "${mentee.firstName ?: ""} ${mentee.lastName ?: ""}".trim().ifEmpty { "ten uczestnik" }
    val companyName = mentee.companyName
    val ticketName = mentee.ticketName

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.HowToReg,
                contentDescription = null,
                tint = Color(0xFF00897B)
            )
        },
        title = { Text("Potwierdzić przybycie?") },
        text = {
            Column {
                Text("Czy potwierdzasz przybycie:")
                Spacer(Modifier.height(8.dp))
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (!companyName.isNullOrBlank()) {
                    Text(companyName, fontSize = 13.sp, color = Color.Gray)
                }
                if (!ticketName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Bilet: $ticketName", fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00897B))
            ) {
                Text("Tak, zarejestruj przybycie")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
