package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalUriHandler
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
import pl.medidesk.mobile.core.ui.theme.StatusColors
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
        // Outer MainScreen Scaffold już zarezerwowało miejsce na NavigationBar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Moi podopieczni", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    if (state.companies.isNotEmpty()) {
                        val totalPeople = state.companies.sumOf { it.count }
                        Text(
                            "${state.companies.size} firm · $totalPeople os.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.secondary
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Brak podopiecznych",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Nie masz przypisanych uczestników do tego wydarzenia",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    var showMenu by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))

                // Lewa kolumna: nazwa + subtitle z liczbą
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.companyName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (group.isPartner) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "PARTNER",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    // Subtitle: shortName · N os.
                    val subtitle = buildString {
                        if (group.shortName.isNotBlank()) {
                            append(group.shortName)
                            append(" · ")
                        }
                        append("${group.count} os.")
                    }
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Prawa strona: status + analytics icon + 3 kropki
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(group)

                    if (group.crmAccountId != null) {
                        IconButton(
                            onClick = {
                                uriHandler.openUri("https://panel.medidesk.edu.pl/admin/crm/accounts/${group.crmAccountId}")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = "Analityka CRM",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Więcej opcji",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isWithdrawing) "Wycofywanie…" else "Wycofaj opiekę",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showWithdrawDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.RemoveModerator,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                enabled = !isWithdrawing
                            )
                        }
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
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
        group.pendingCount > 0 -> StatusPill("OCZEKUJE", StatusColors.Pending)
        group.freeCount == group.count -> StatusPill(
            label = "FREE",
            fg = MaterialTheme.colorScheme.onSurfaceVariant,
            bg = MaterialTheme.colorScheme.surfaceVariant
        )
        else -> StatusPill("OPŁACONE", StatusColors.Paid)
    }
}

@Composable
private fun StatusPill(
    label: String,
    fg: Color,
    bg: Color = fg.copy(alpha = 0.15f)
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
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
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayName = "${participant.firstName ?: ""} ${participant.lastName ?: ""}"
                .trim()
                .ifEmpty { "—" }
            Text(
                displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!participant.phone.isNullOrBlank()) {
                Text(participant.phone!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!participant.email.isNullOrBlank()) {
                Text(
                    participant.email!!,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Prawa strona: badge biletu (opcjonalny) + akcja check-in
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            val ticketLabel = participant.ticketName?.takeIf { it.isNotBlank() }
            if (ticketLabel != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        ticketLabel.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Box {
                val accent = MaterialTheme.colorScheme.secondary
                when {
                participant.checkedIn -> {
                    Icon(
                        Icons.Default.HowToReg,
                        contentDescription = "Zarejestrowany",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                isCheckingIn -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                }
                else -> {
                    IconButton(
                        onClick = onCheckInClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.Default.HowToReg,
                            contentDescription = "Potwierdź przybycie",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                }  // closes when
            }      // closes Box
        }          // closes inner Row (badge + check-in)
    }              // closes outer Row
}                  // closes ParticipantRow

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
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        title = { Text("Potwierdzić przybycie?") },
        text = {
            Column {
                Text("Czy potwierdzasz przybycie:")
                Spacer(Modifier.height(8.dp))
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (!companyName.isNullOrBlank()) {
                    Text(companyName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!ticketName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Bilet: $ticketName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Tak, zarejestruj przybycie")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
