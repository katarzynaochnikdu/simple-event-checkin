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
import javax.inject.Inject

sealed class ParticipantDetailsUiState {
    data object Loading : ParticipantDetailsUiState()
    data class Success(val participant: Participant) : ParticipantDetailsUiState()
    data class Error(val message: String) : ParticipantDetailsUiState()
}

@HiltViewModel
class ParticipantDetailsViewModel @Inject constructor(
    private val participantDao: ParticipantDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val participantId: Long? = savedStateHandle.get<Long>("participantId")

    private val _uiState = MutableStateFlow<ParticipantDetailsUiState>(ParticipantDetailsUiState.Loading)
    val uiState: StateFlow<ParticipantDetailsUiState> = _uiState.asStateFlow()

    init {
        Log.d("ParticipantDetails", "Init with ID: $participantId")
        participantId?.let { loadParticipant(it) } ?: run {
            _uiState.value = ParticipantDetailsUiState.Error("Błędne ID uczestnika")
        }
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
                            buyerEmail = entity.buyerEmail
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
