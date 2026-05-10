package pl.medidesk.mobile.feature.participants.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.database.dao.TicketClassDao
import pl.medidesk.mobile.core.database.entities.OfflineCheckinEntity
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.model.TicketClass
import pl.medidesk.mobile.core.sync.SyncEngine
import java.time.Instant
import javax.inject.Inject

data class ParticipantsUiState(
    val participants: List<Participant> = emptyList(),
    val filteredParticipants: List<Participant> = emptyList(),
    val ticketClasses: List<TicketClass> = emptyList(),
    val searchQuery: String = "",
    val filterCheckedIn: Boolean? = null,
    val selectedTicketClassId: String? = null,
    val syncState: SyncState = SyncState(),
    val isRefreshing: Boolean = false,
    val checkinDialogParticipant: Participant? = null,
    val checkoutDialogParticipant: Participant? = null
)

@HiltViewModel
class ParticipantsViewModel @Inject constructor(
    private val participantDao: ParticipantDao,
    private val ticketClassDao: TicketClassDao,
    private val offlineCheckinDao: OfflineCheckinDao,
    private val syncEngine: SyncEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = savedStateHandle.get<String>("eventId") ?: ""

    private val _uiState = MutableStateFlow(ParticipantsUiState())
    val uiState: StateFlow<ParticipantsUiState> = _uiState.asStateFlow()

    init {
        Log.d("ParticipantsVM", "Initializing for eventId: $eventId")
        
        viewModelScope.launch {
            participantDao.getParticipantsFlow(eventId).collect { entities ->
                Log.d("ParticipantsVM", "Received ${entities.size} entities from DB")
                val participants = entities.map { e ->
                    Participant(
                        id = e.id,
                        ticketId = e.ticketId,
                        backstageTicketId = e.backstageTicketId,
                        firstName = e.firstName,
                        lastName = e.lastName,
                        email = e.email,
                        phone = e.phone,
                        company = e.company,
                        ticketClassId = e.ticketClassId,
                        ticketName = e.ticketName,
                        status = e.status,
                        attendanceStatus = e.attendanceStatus,
                        eventOrderId = e.eventOrderId,
                        eventId = e.eventId,
                        checkedInAt = e.checkedInAt,
                        orderStatus = e.orderStatus,
                        isWalkin = e.isWalkin,
                        tags = e.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                        buyerName = e.buyerName,
                        buyerEmail = e.buyerEmail,
                        paymentMethod = e.paymentMethod,
                        purchaserNip = e.purchaserNip,
                        purchaserCompany = e.purchaserCompany,
                        orderParticipantsTotal = e.orderParticipantsTotal,
                        orderParticipantsCheckedIn = e.orderParticipantsCheckedIn
                    )
                }
                val current = _uiState.value
                _uiState.value = current.copy(
                    participants = participants,
                    filteredParticipants = applyFilters(participants, current.searchQuery, current.filterCheckedIn, current.selectedTicketClassId)
                )
            }
        }
        
        viewModelScope.launch {
            ticketClassDao.getTicketClassesFlow(eventId).collect { entities ->
                val classes = entities.map { TicketClass(it.ticketClassId, it.ticketName, it.eventId) }
                _uiState.value = _uiState.value.copy(ticketClasses = classes)
            }
        }

        viewModelScope.launch {
            syncEngine.syncState.collect { syncState ->
                _uiState.value = _uiState.value.copy(syncState = syncState)
            }
        }

        if (eventId.isNotEmpty()) {
            refresh(eventId)
        }
    }

    fun onSearchQuery(query: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            searchQuery = query,
            filteredParticipants = applyFilters(current.participants, query, current.filterCheckedIn, current.selectedTicketClassId)
        )
    }

    fun onFilterCheckedIn(filter: Boolean?) {
        val current = _uiState.value
        _uiState.value = current.copy(
            filterCheckedIn = filter,
            filteredParticipants = applyFilters(current.participants, current.searchQuery, filter, current.selectedTicketClassId)
        )
    }
    
    fun onFilterTicketClass(classId: String?) {
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedTicketClassId = classId,
            filteredParticipants = applyFilters(current.participants, current.searchQuery, current.filterCheckedIn, classId)
        )
    }

    fun refresh(eventId: String) {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        viewModelScope.launch {
            try {
                syncEngine.runImmediateSyncAndWait(eventId)
                Log.d("ParticipantsVM", "Sync finished — local DB should now reflect server state")
            } catch (e: Exception) {
                Log.e("ParticipantsVM", "Sync wait failed: ${e.message}", e)
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    // Manual Actions
    fun showCheckinDialog(participant: Participant) {
        _uiState.value = _uiState.value.copy(checkinDialogParticipant = participant)
    }
    
    fun showCheckoutDialog(participant: Participant) {
        _uiState.value = _uiState.value.copy(checkoutDialogParticipant = participant)
    }
    
    fun dismissDialogs() {
        _uiState.value = _uiState.value.copy(checkinDialogParticipant = null, checkoutDialogParticipant = null)
    }

    fun performManualCheckin(participant: Participant) {
        viewModelScope.launch {
            val now = Instant.now().toString()
            val identifier = participant.ticketId ?: participant.backstageTicketId
            Log.d("ParticipantsVM", "Performing manual check-in for ID: ${participant.id}, TicketID: $identifier")

            participantDao.markCheckedInById(participant.id, now)

            if (identifier != null) {
                offlineCheckinDao.insert(
                    OfflineCheckinEntity(
                        ticketId = identifier,
                        eventId = participant.eventId,
                        scannedAt = now,
                        action = "checkin"
                    )
                )
                syncEngine.triggerImmediateSync(participant.eventId)
            } else {
                Log.e("ParticipantsVM", "Cannot sync check-in: no ticketId for participant ${participant.id}")
            }
            
            dismissDialogs()
        }
    }
    
    fun performManualCheckout(participant: Participant) {
        viewModelScope.launch {
            val now = Instant.now().toString()
            Log.d("ParticipantsVM", "Performing manual check-out for ID: ${participant.id}")
            
            // 1. Update local DB
            participantDao.markCheckedOutById(participant.id)
            
            val identifier = participant.ticketId ?: participant.backstageTicketId
            if (identifier != null) {
                offlineCheckinDao.insert(
                    OfflineCheckinEntity(
                        ticketId = identifier,
                        eventId = participant.eventId,
                        scannedAt = now,
                        action = "checkout"
                    )
                )
                // 3. Trigger sync
                syncEngine.triggerImmediateSync(participant.eventId)
            }

            dismissDialogs()
        }
    }

    private fun applyFilters(
        all: List<Participant>,
        query: String,
        checkedInFilter: Boolean?,
        classIdFilter: String?
    ): List<Participant> {
        return all.filter { p ->
            val notCancelled = p.orderStatus?.lowercase() !in listOf("cancelled", "refunded")

            val matchesQuery = query.isBlank() ||
                p.displayName.contains(query, ignoreCase = true) ||
                p.email?.contains(query, ignoreCase = true) == true ||
                p.company?.contains(query, ignoreCase = true) == true ||
                p.ticketId?.contains(query, ignoreCase = true) == true ||
                p.backstageTicketId?.contains(query, ignoreCase = true) == true ||
                p.buyerName?.contains(query, ignoreCase = true) == true ||
                p.tags.any { it.contains(query, ignoreCase = true) }

            val matchesChecked = when (checkedInFilter) {
                true -> p.isCheckedIn
                false -> !p.isCheckedIn
                null -> true
            }

            val matchesClass = classIdFilter == null || p.ticketClassId == classIdFilter

            notCancelled && matchesQuery && matchesChecked && matchesClass
        }
    }
}
