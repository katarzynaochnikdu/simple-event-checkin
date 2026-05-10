package pl.medidesk.mobile.feature.dashboard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.model.*
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.DashboardResponse
import pl.medidesk.mobile.core.sync.SyncEngine
import pl.medidesk.mobile.feature.events.domain.repository.EventsRepository
import retrofit2.Response
import javax.inject.Inject

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(val data: DashboardData, val syncState: SyncState, val user: User) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val apiService: MobileApiService,
    private val syncEngine: SyncEngine,
    private val participantDao: ParticipantDao,
    private val eventsRepository: EventsRepository,
    private val authDataStore: AuthDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Re-load dashboard whenever any screen reports a check-in/undo, so the
        // POSTĘP CHECK-IN counter and ODZNACZENI/OCZEKUJĄCY tiles update without
        // user having to leave and return to the screen.
        viewModelScope.launch {
            syncEngine.participantChanges.collect {
                val current = _uiState.value
                val eventId = if (current is DashboardUiState.Success) current.data.eventId else return@collect
                if (eventId.isNotBlank() && eventId != "0") loadDashboard(eventId)
            }
        }

        viewModelScope.launch {
            val user = combine(
                authDataStore.userEmailFlow,
                authDataStore.userRoleFlow,
                authDataStore.userIdFlow,
                authDataStore.userFirstNameFlow,
                authDataStore.userLastNameFlow
            ) { email, role, id, first, last ->
                User(id?.toIntOrNull() ?: 0, email ?: "", first ?: "", last ?: "", role ?: "PARTICIPANT")
            }.first()
            
            if (_uiState.value is DashboardUiState.Loading) {
                 _uiState.value = DashboardUiState.Success(
                    DashboardData("0", 0, 0, 0, 0, 0.0),
                    SyncState(),
                    user
                 )
            }
        }
    }

    fun loadDashboard(eventId: String) {
        if (eventId == "0" || eventId.isBlank()) return

        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            // Wait for sync (push pending checkins, then full pull) BEFORE asking backend
            // for dashboard stats — otherwise getDashboard may return stale numbers that
            // don't reflect the user's last actions yet.
            try { syncEngine.runImmediateSyncAndWait(eventId) } catch (_: Exception) {}
            
            val userFlow = combine(
                authDataStore.userEmailFlow,
                authDataStore.userRoleFlow,
                authDataStore.userIdFlow,
                authDataStore.userFirstNameFlow,
                authDataStore.userLastNameFlow
            ) { email, role, id, first, last ->
                User(id?.toIntOrNull() ?: 0, email ?: "", first ?: "", last ?: "", role ?: "PARTICIPANT")
            }

            val dashboardFlow: Flow<Response<DashboardResponse>?> = flow { 
                try { emit(apiService.getDashboard(eventId)) } catch (e: Exception) { emit(null) }
            }

            val eventInfoFlow: Flow<EventItem?> = flow {
                val eventsResult = eventsRepository.getEvents()
                val list = eventsResult.getOrNull()
                // Flexible matching
                emit(list?.find { it.eventId == eventId || it.eventId == eventId.replace("-", "") })
            }

            combine(
                userFlow,
                dashboardFlow,
                participantDao.countTotalFlow(eventId),
                participantDao.countCheckedInFlow(eventId),
                participantDao.getRecentCheckinsFlow(eventId),
                eventInfoFlow,
                syncEngine.syncState
            ) { args: Array<Any?> ->
                val user = args[0] as User
                val response = args[1] as? Response<DashboardResponse>
                val localTotal = args[2] as Int
                val localCheckedIn = args[3] as Int
                val recentEntities = args[4] as List<*>
                val eventInfo = args[5] as? EventItem
                val syncState = args[6] as SyncState

                val participants = recentEntities.mapNotNull { it as? Participant }
                val body = response?.body()
                
                val total = body?.totalRegistered ?: localTotal
                val checked = body?.checkedIn ?: localCheckedIn
                
                val data = DashboardData(
                    eventId = body?.eventId ?: eventId,
                    totalRegistered = total,
                    totalWithQr = body?.totalWithQr ?: total,
                    checkedIn = checked,
                    walkIns = body?.walkIns ?: 0,
                    checkInRate = if (total > 0) (checked.toDouble() / total.toDouble() * 100.0) else 0.0,
                    byTicketClass = body?.byTicketClass?.map { TicketClassStat(it.ticketName, it.total, it.checkedIn) } ?: emptyList(),
                    timeline = body?.timeline?.map { TimelineEntry(it.hour, it.count) } ?: emptyList(),
                    topScanners = body?.topScanners?.map { ScannerStat(it.email, it.count) } ?: emptyList(),
                    recentCheckins = participants,
                    eventName = eventInfo?.eventName ?: body?.eventId ?: "Wydarzenie",
                    startDate = eventInfo?.startDate ?: "",
                    venue = eventInfo?.venue ?: "",
                    imageUrl = eventInfo?.imageUrl,
                    logoUrl = body?.logoUrl ?: eventInfo?.logoUrl,
                    primaryColor = body?.primaryColor ?: eventInfo?.primaryColor,
                    secondaryColor = body?.secondaryColor ?: eventInfo?.secondaryColor,
                    accentColor = body?.accentColor ?: eventInfo?.accentColor
                )
                
                DashboardUiState.Success(data, syncState, user)
            }.collect { _uiState.value = it }
        }
    }
    
    fun triggerSync(eventId: String) {
        syncEngine.triggerImmediateSync(eventId)
    }
}
