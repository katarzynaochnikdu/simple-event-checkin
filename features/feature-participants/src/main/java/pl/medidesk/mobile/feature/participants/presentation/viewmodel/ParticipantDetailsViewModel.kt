package pl.medidesk.mobile.feature.participants.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.CheckinRequest
import pl.medidesk.mobile.core.network.dto.UndoCheckinRequest
import java.time.Instant
import javax.inject.Inject

sealed class ParticipantDetailsUiState {
    data object Loading : ParticipantDetailsUiState()
    data class Success(val participant: Participant) : ParticipantDetailsUiState()
    data class Error(val message: String) : ParticipantDetailsUiState()
}

sealed class CheckinResult {
    data object Idle : CheckinResult()
    data object Loading : CheckinResult()
    data object Success : CheckinResult()
    data object UndoSuccess : CheckinResult()
    data object AlreadyCheckedIn : CheckinResult()
    data class Failure(val message: String) : CheckinResult()
}

@HiltViewModel
class ParticipantDetailsViewModel @Inject constructor(
    private val participantDao: ParticipantDao,
    private val apiService: MobileApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val participantId: Long? = savedStateHandle.get<Long>("participantId")

    private val _uiState = MutableStateFlow<ParticipantDetailsUiState>(ParticipantDetailsUiState.Loading)
    val uiState: StateFlow<ParticipantDetailsUiState> = _uiState.asStateFlow()

    private val _checkinResult = MutableStateFlow<CheckinResult>(CheckinResult.Idle)
    val checkinResult: StateFlow<CheckinResult> = _checkinResult.asStateFlow()

    init {
        Log.d("ParticipantDetails", "Init with ID: $participantId")
        participantId?.let { loadParticipant(it) } ?: run {
            _uiState.value = ParticipantDetailsUiState.Error("Błędne ID uczestnika")
        }
    }

    fun performCheckin() {
        val participant = (_uiState.value as? ParticipantDetailsUiState.Success)?.participant ?: return
        if (participant.isCheckedIn) {
            _checkinResult.value = CheckinResult.AlreadyCheckedIn
            return
        }
        val ticketId = participant.backstageTicketId ?: participant.ticketId ?: return

        viewModelScope.launch {
            _checkinResult.value = CheckinResult.Loading
            try {
                val response = apiService.checkin(
                    CheckinRequest(
                        ticketId = ticketId,
                        eventId = participant.eventId,
                        scannedAt = Instant.now().toString(),
                        deviceId = "manual"
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        val ts = body.checkedInAt ?: Instant.now().toString()
                        participantDao.markCheckedInById(participant.id, ts)
                        loadParticipant(participant.id)
                        _checkinResult.value = if (body.alreadyCheckedIn)
                            CheckinResult.AlreadyCheckedIn else CheckinResult.Success
                    } else {
                        _checkinResult.value = CheckinResult.Failure(body?.error ?: "Błąd check-in")
                    }
                } else {
                    _checkinResult.value = CheckinResult.Failure("Błąd sieci: ${response.code()}")
                }
            } catch (e: Exception) {
                _checkinResult.value = CheckinResult.Failure(e.message ?: "Nieznany błąd")
            }
        }
    }

    fun performUndoCheckin() {
        val participant = (_uiState.value as? ParticipantDetailsUiState.Success)?.participant ?: return
        if (!participant.isCheckedIn) return
        val ticketId = participant.backstageTicketId ?: participant.ticketId ?: return

        viewModelScope.launch {
            _checkinResult.value = CheckinResult.Loading
            try {
                val response = apiService.undoCheckin(
                    UndoCheckinRequest(
                        ticketId = ticketId,
                        eventId = participant.eventId,
                        deviceId = "manual"
                    )
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    participantDao.markCheckedOutById(participant.id)
                    loadParticipant(participant.id)
                    _checkinResult.value = CheckinResult.UndoSuccess
                } else {
                    val err = response.body()?.error ?: "Błąd cofania check-in"
                    _checkinResult.value = CheckinResult.Failure(err)
                }
            } catch (e: Exception) {
                _checkinResult.value = CheckinResult.Failure(e.message ?: "Nieznany błąd")
            }
        }
    }

    fun resetCheckinResult() {
        _checkinResult.value = CheckinResult.Idle
    }

    fun loadParticipant(id: Long) {
        viewModelScope.launch {
            _uiState.value = ParticipantDetailsUiState.Loading
            try {
                val entity = participantDao.getParticipantById(id)
                if (entity != null) {
                    _uiState.value = ParticipantDetailsUiState.Success(
                        Participant(
                            id = entity.id,
                            ticketId = entity.ticketId,
                            backstageTicketId = entity.backstageTicketId,
                            firstName = entity.firstName,
                            lastName = entity.lastName,
                            email = entity.email,
                            company = entity.company,
                            ticketClassId = entity.ticketClassId,
                            ticketName = entity.ticketName,
                            status = entity.status,
                            attendanceStatus = entity.attendanceStatus,
                            eventOrderId = entity.eventOrderId,
                            eventId = entity.eventId,
                            checkedInAt = entity.checkedInAt,
                            orderStatus = entity.orderStatus,
                            isWalkin = entity.isWalkin,
                            tags = entity.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                            buyerName = entity.buyerName,
                            buyerEmail = entity.buyerEmail,
                            paymentMethod = entity.paymentMethod,
                            purchaserNip = entity.purchaserNip,
                            purchaserCompany = entity.purchaserCompany,
                            orderParticipantsTotal = entity.orderParticipantsTotal,
                            orderParticipantsCheckedIn = entity.orderParticipantsCheckedIn
                        )
                    )
                } else {
                    _uiState.value = ParticipantDetailsUiState.Error("Nie znaleziono uczestnika w bazie")
                }
            } catch (e: Exception) {
                _uiState.value = ParticipantDetailsUiState.Error(e.message ?: "Błąd bazy danych")
            }
        }
    }
}
