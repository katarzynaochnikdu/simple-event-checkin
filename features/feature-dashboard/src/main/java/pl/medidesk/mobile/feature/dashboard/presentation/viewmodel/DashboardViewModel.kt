package pl.medidesk.mobile.feature.dashboard.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.database.entities.ParticipantEntity
import pl.medidesk.mobile.core.mappers.toDomain
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.model.*
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.analytics.Analytics
import pl.medidesk.mobile.core.analytics.AnalyticsEvent
import pl.medidesk.mobile.core.network.dto.DashboardResponse
import pl.medidesk.mobile.core.network.dto.SpeakerCheckinStatsDto
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

        // (Removed: empty-Success bootstrap with primaryColor=null. It caused the
        // ProgressCard to render with MaterialTheme.colorScheme.primary (granat) for
        // ~1s before the real dashboard arrived with the event's accent — visible
        // color flash. Now we just stay in Loading until loadDashboard completes,
        // so the colored card appears in the right color from the first frame.)
    }

    fun loadDashboard(eventId: String) {
        if (eventId == "0" || eventId.isBlank()) return

        viewModelScope.launch {
            // Only show Loading on the very first load. On subsequent calls
            // (LifecycleResumeEffect when user comes back) keep the previous
            // Success on screen so we don't flash a Loading skeleton — new
            // numbers will overwrite when sync + getDashboard return.
            val isFirstLoad = _uiState.value !is DashboardUiState.Success
            if (isFirstLoad) {
                _uiState.value = DashboardUiState.Loading
                Analytics.capture(AnalyticsEvent.EVENT_OPENED, mapOf(AnalyticsEvent.Props.EVENT_ID to eventId))
            }

            // Fire-and-forget sync — don't block UI while network does its job.
            // SQLite flows (countTotalFlow, countCheckedInFlow, getRecentCheckinsFlow)
            // are reactive: they re-emit automatically when SyncWorker writes new rows,
            // so the dashboard will update in-place without showing a loading spinner.
            syncEngine.triggerImmediateSync(eventId)
            
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

            // WO-MOB-015: speakers attendance counters (best-effort — falls back to 0/0 on failure)
            val speakerStatsFlow: Flow<SpeakerCheckinStatsDto?> = flow {
                try { emit(apiService.speakerCheckinStats(eventId).body()) } catch (e: Exception) { emit(null) }
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
                participantDao.getCheckedInParticipantsFlow(eventId),
                eventInfoFlow,
                syncEngine.syncState,
                speakerStatsFlow
            ) { args: Array<Any?> ->
                val user = args[0] as User
                val response = args[1] as? Response<DashboardResponse>
                val localTotal = args[2] as Int
                val localCheckedIn = args[3] as Int
                // Room query returns List<ParticipantEntity> — must map through toDomain()
                // (the previous `it as? Participant` cast silently dropped every row → TOP FIRMY always empty).
                val checkedIn = (args[4] as List<*>).filterIsInstance<ParticipantEntity>().map { it.toDomain() }
                val eventInfo = args[5] as? EventItem
                val syncState = args[6] as SyncState
                val speakerStats = args[7] as? SpeakerCheckinStatsDto

                // TOP FIRMY ranking: per-company count of checked-in attendees.
                // Fallback company -> purchaserCompany because per-participant `company` is often blank
                // in this product; real org name lives in purchaser_company.
                val companyStats = checkedIn
                    .map { p -> p.company?.takeIf { it.isNotBlank() } ?: p.purchaserCompany }
                    .filterNot { it.isNullOrBlank() }
                    .groupingBy { it!!.trim() }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .take(8)
                    .map { CompanyStat(it.first, it.second) }

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
                    companyStats = companyStats,
                    eventName = eventInfo?.eventName ?: body?.eventId ?: "Wydarzenie",
                    startDate = eventInfo?.startDate ?: "",
                    venue = eventInfo?.venue ?: "",
                    imageUrl = eventInfo?.imageUrl,
                    logoUrl = body?.logoUrl ?: eventInfo?.logoUrl,
                    primaryColor = body?.primaryColor ?: eventInfo?.primaryColor,
                    secondaryColor = body?.secondaryColor ?: eventInfo?.secondaryColor,
                    accentColor = body?.accentColor ?: eventInfo?.accentColor,
                    speakersTotal = speakerStats?.total ?: 0,
                    speakersAttended = speakerStats?.attended ?: 0
                )

                DashboardUiState.Success(data, syncState, user)
            }.collect { _uiState.value = it }
        }
    }
    
    fun triggerSync(eventId: String) {
        syncEngine.triggerImmediateSync(eventId)
    }
}
