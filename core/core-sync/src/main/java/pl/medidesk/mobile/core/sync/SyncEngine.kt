package pl.medidesk.mobile.core.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.SpeakerCheckinDao
import pl.medidesk.mobile.core.database.dao.WalkinDao
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.model.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight broadcast event so screens that don't share the participants Flow
 * (MyMentees pulls from a separate /my-mentees endpoint) can update instantly
 * after a check-in done elsewhere — without each screen running its own poll.
 */
data class ParticipantStatusChange(
    val ticketId: String,
    val participantId: Long?,
    val isCheckedIn: Boolean,
    val checkedInAt: String?
)

@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineCheckinDao: OfflineCheckinDao,
    private val walkinDao: WalkinDao,
    private val speakerCheckinDao: SpeakerCheckinDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    private val _participantChanges = MutableSharedFlow<ParticipantStatusChange>(extraBufferCapacity = 16)
    val participantChanges: SharedFlow<ParticipantStatusChange> = _participantChanges.asSharedFlow()

    fun notifyParticipantChanged(change: ParticipantStatusChange) {
        _participantChanges.tryEmit(change)
    }

    val syncState: StateFlow<SyncState> = combine(
        offlineCheckinDao.getUnsyncedCountFlow(),
        walkinDao.getPendingCountFlow(),
        speakerCheckinDao.getUnsyncedCountFlow()
    ) { pendingCheckins, pendingWalkins, pendingSpeakerCheckins ->
        SyncState(
            status = if (pendingCheckins > 0 || pendingWalkins > 0 || pendingSpeakerCheckins > 0) SyncStatus.IDLE else SyncStatus.SUCCESS,
            pendingCheckins = pendingCheckins + pendingSpeakerCheckins,
            pendingWalkins = pendingWalkins
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncState())

    fun startPeriodicSync(eventId: String) {
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.REPLACE,
            SyncWorker.periodicWorkRequest(eventId)
        )
    }

    fun triggerImmediateSync(eventId: String) {
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            SyncWorker.immediateWorkRequest(eventId)
        )
    }

    /**
     * Suspend wrapper — kicks immediate sync and waits until WorkManager reports terminal state.
     * Use from ViewModel.refresh() so the UI knows when fresh data is in SQLite and can stop
     * showing "Refreshing" indicator. Returns when WorkInfo.state is SUCCEEDED/FAILED/CANCELLED.
     */
    suspend fun runImmediateSyncAndWait(eventId: String) {
        val request = SyncWorker.immediateWorkRequest(eventId)
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request
        )
        workManager.getWorkInfoByIdFlow(request.id).firstOrNull { info ->
            info != null && info.state.isFinished
        }
    }

    fun stopPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
    }

    /**
     * Anuluje zakolejkowany/wykonujący się immediate sync — używane przy logout (WO-MOB-028),
     * żeby straggler-worker nie odpalił się PO wipe bez tokenu (401 → pętla notifySessionExpired
     * resetująca ekran logowania). Mieszka tu, a nie w LogoutUseCase, bo SyncEngine jest
     * właścicielem lifecycle'u sync worków (start/stop/trigger) i już trzyma WorkManager.
     */
    fun cancelImmediateSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_IMMEDIATE)
    }
}
