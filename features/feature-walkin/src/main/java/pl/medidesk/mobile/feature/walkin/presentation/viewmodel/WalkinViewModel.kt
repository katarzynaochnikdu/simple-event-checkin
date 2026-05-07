package pl.medidesk.mobile.feature.walkin.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.database.dao.TicketClassDao
import pl.medidesk.mobile.core.database.dao.WalkinDao
import pl.medidesk.mobile.core.database.entities.WalkinEntity
import pl.medidesk.mobile.core.model.TicketClass
import pl.medidesk.mobile.core.model.WalkinParticipant
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.WalkinRequest
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class WalkinUiState(
    val walkins: List<WalkinParticipant> = emptyList(),
    val ticketClasses: List<TicketClass> = emptyList(),
    val isLoading: Boolean = false,
    val showForm: Boolean = false,
    val formSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WalkinViewModel @Inject constructor(
    private val walkinDao: WalkinDao,
    private val ticketClassDao: TicketClassDao,
    private val apiService: MobileApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalkinUiState())
    val uiState: StateFlow<WalkinUiState> = _uiState.asStateFlow()

    fun loadData(eventId: String) {
        viewModelScope.launch {
            val tcs = ticketClassDao.getForEvent(eventId).map { tc ->
                TicketClass(tc.ticketClassId, tc.ticketName, tc.eventId)
            }
            _uiState.value = _uiState.value.copy(ticketClasses = tcs)

            walkinDao.getWalkinsFlow(eventId).collect { entities ->
                val walkins = entities.map { e ->
                    WalkinParticipant(e.id, e.walkInCode, e.eventId, e.firstName, e.lastName,
                        e.email, e.phone, e.company, e.ticketClassId, e.ticketName, e.notes,
                        e.checkedInAt, e.status, e.createdAt, e.syncStatus)
                }
                _uiState.value = _uiState.value.copy(walkins = walkins)
            }
        }
    }

    fun createWalkin(
        eventId: String, firstName: String, lastName: String,
        email: String?, phone: String?, company: String?,
        ticketClassId: String?, ticketName: String?, notes: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val walkInCode = "WI_${UUID.randomUUID().toString().take(8).uppercase()}"
            val now = Instant.now().toString()

            // Try online first
            try {
                val response = apiService.createWalkin(
                    WalkinRequest(eventId, firstName, lastName, walkInCode, email, phone, company,
                        ticketClassId, notes, now)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.value = _uiState.value.copy(isLoading = false, showForm = false, formSaved = true)
                    return@launch
                }
            } catch (_: Exception) { /* fall through to offline */ }

            // Offline queue
            walkinDao.insert(WalkinEntity(
                walkInCode = walkInCode, eventId = eventId, firstName = firstName, lastName = lastName,
                email = email, phone = phone, company = company, ticketClassId = ticketClassId,
                ticketName = ticketName, notes = notes, checkedInAt = now, createdAt = now,
                syncStatus = "pending"
            ))
            _uiState.value = _uiState.value.copy(isLoading = false, showForm = false, formSaved = true)
        }
    }

    fun showForm() { _uiState.value = _uiState.value.copy(showForm = true, formSaved = false) }
    fun hideForm() { _uiState.value = _uiState.value.copy(showForm = false) }
    fun resetFormSaved() { _uiState.value = _uiState.value.copy(formSaved = false) }
}
