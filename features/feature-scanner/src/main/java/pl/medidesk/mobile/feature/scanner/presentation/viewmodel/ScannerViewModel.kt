package pl.medidesk.mobile.feature.scanner.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.CheckinResult
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.sync.SyncEngine
import pl.medidesk.mobile.core.sync.CheckinUseCase
import javax.inject.Inject

enum class ScanFeedback { NONE, PROCESSING, SUCCESS, SUCCESS_OFFLINE, DUPLICATE, ERROR, NOT_FOUND }

data class ScannerUiState(
    val feedback: ScanFeedback = ScanFeedback.NONE,
    val lastResult: CheckinResult? = null,
    val syncState: SyncState = SyncState(),
    val isScanning: Boolean = true
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val checkinUseCase: CheckinUseCase,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var lastScannedTicketId: String? = null

    init {
        viewModelScope.launch {
            syncEngine.syncState.collect { syncState ->
                _uiState.value = _uiState.value.copy(syncState = syncState)
            }
        }
    }

    fun onQrScanned(ticketId: String, eventId: String) {
        // Debounce: ignore same ticket scanned within 3 seconds
        if (ticketId == lastScannedTicketId) return
        lastScannedTicketId = ticketId

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(feedback = ScanFeedback.PROCESSING, isScanning = false)
            val result = checkinUseCase(ticketId, eventId)
            val feedback = when {
                !result.success && result.error == "not_found" -> ScanFeedback.NOT_FOUND
                !result.success -> ScanFeedback.ERROR
                result.alreadyCheckedIn -> ScanFeedback.DUPLICATE
                result.isOffline -> ScanFeedback.SUCCESS_OFFLINE
                else -> ScanFeedback.SUCCESS
            }
            _uiState.value = _uiState.value.copy(feedback = feedback, lastResult = result)

            // Auto-dismiss after 3s
            delay(3000)
            _uiState.value = _uiState.value.copy(feedback = ScanFeedback.NONE, lastResult = null, isScanning = true)
            lastScannedTicketId = null
        }
    }
}
