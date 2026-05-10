package pl.medidesk.mobile.feature.scanner.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.CheckinResult
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.sync.CheckinUseCase
import pl.medidesk.mobile.core.sync.LookupParticipantByTicketUseCase
import pl.medidesk.mobile.core.sync.LookupResult
import pl.medidesk.mobile.core.sync.SyncEngine
import pl.medidesk.mobile.core.sync.UndoCheckinUseCase
import javax.inject.Inject

enum class ScanFeedback { NONE, PROCESSING, SUCCESS, SUCCESS_OFFLINE, DUPLICATE, ERROR, NOT_FOUND, UNDOING, UNDONE }

/**
 * Stan oczekiwania na potwierdzenie check-in po zeskanowaniu QR.
 * Operator widzi dialog "Potwierdzenie Check-In" zanim żądanie poleci na backend.
 * Dla uczestników nieobecnych w lokalnej bazie (jeszcze nie zsynchronizowani)
 * `participantName` może być pusty — dialog pokazuje wtedy generyczny tekst.
 */
data class PendingScan(
    val ticketId: String,
    val eventId: String,
    val participantName: String,
    val ticketName: String,
    val company: String,
    val knownLocally: Boolean,
    val alreadyCheckedIn: Boolean
)

data class ScannerUiState(
    val feedback: ScanFeedback = ScanFeedback.NONE,
    val lastResult: CheckinResult? = null,
    val syncState: SyncState = SyncState(),
    val isScanning: Boolean = true,
    val pendingScan: PendingScan? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val checkinUseCase: CheckinUseCase,
    private val undoCheckinUseCase: UndoCheckinUseCase,
    private val lookupUseCase: LookupParticipantByTicketUseCase,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var lastScannedTicketId: String? = null
    private var lastScannedEventId: String? = null
    private var autoDismissJob: Job? = null

    init {
        viewModelScope.launch {
            syncEngine.syncState.collect { syncState ->
                _uiState.value = _uiState.value.copy(syncState = syncState)
            }
        }
    }

    /**
     * Krok 1: kamera wykryła QR. Pauzujemy skaner i pokazujemy dialog potwierdzenia.
     * Faktyczny check-in odpala się dopiero po `confirmScan()`.
     */
    fun onQrScanned(ticketId: String, eventId: String) {
        if (ticketId == lastScannedTicketId) return
        if (_uiState.value.pendingScan != null) return
        if (_uiState.value.feedback != ScanFeedback.NONE) return
        lastScannedTicketId = ticketId
        lastScannedEventId = eventId
        autoDismissJob?.cancel()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = false)
            val pending = when (val result = lookupUseCase(ticketId)) {
                is LookupResult.Found -> PendingScan(
                    ticketId = ticketId,
                    eventId = eventId,
                    participantName = result.participantName,
                    ticketName = result.ticketName,
                    company = result.company,
                    knownLocally = true,
                    alreadyCheckedIn = result.alreadyCheckedIn
                )
                LookupResult.NotFound -> PendingScan(
                    ticketId = ticketId,
                    eventId = eventId,
                    participantName = "",
                    ticketName = "",
                    company = "",
                    knownLocally = false,
                    alreadyCheckedIn = false
                )
            }
            _uiState.value = _uiState.value.copy(pendingScan = pending)
        }
    }

    /** Krok 2: użytkownik kliknął "Tak, Check-In" w dialogu potwierdzenia. */
    fun confirmScan() {
        val pending = _uiState.value.pendingScan ?: return
        autoDismissJob?.cancel()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pendingScan = null,
                feedback = ScanFeedback.PROCESSING
            )
            val result = checkinUseCase(pending.ticketId, pending.eventId)
            val feedback = when {
                !result.success && result.error == "not_found" -> ScanFeedback.NOT_FOUND
                !result.success -> ScanFeedback.ERROR
                result.alreadyCheckedIn -> ScanFeedback.DUPLICATE
                result.isOffline -> ScanFeedback.SUCCESS_OFFLINE
                else -> ScanFeedback.SUCCESS
            }
            _uiState.value = _uiState.value.copy(feedback = feedback, lastResult = result)

            autoDismissJob = viewModelScope.launch {
                delay(3000)
                resetState()
            }
        }
    }

    /** Użytkownik kliknął "Anuluj" w dialogu potwierdzenia. */
    fun cancelScan() {
        autoDismissJob?.cancel()
        resetState()
    }

    fun undoLastScan() {
        val ticketId = lastScannedTicketId ?: return
        val eventId = lastScannedEventId ?: return
        autoDismissJob?.cancel()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(feedback = ScanFeedback.UNDOING, isScanning = false)
            val result = undoCheckinUseCase(ticketId, eventId)

            if (result.success) {
                _uiState.value = _uiState.value.copy(feedback = ScanFeedback.UNDONE, lastResult = result)
                delay(2000)
            } else {
                _uiState.value = _uiState.value.copy(feedback = ScanFeedback.ERROR, lastResult = result)
                delay(3000)
            }
            resetState()
        }
    }

    private fun resetState() {
        _uiState.value = _uiState.value.copy(
            feedback = ScanFeedback.NONE,
            lastResult = null,
            isScanning = true,
            pendingScan = null
        )
        lastScannedTicketId = null
        lastScannedEventId = null
    }
}
