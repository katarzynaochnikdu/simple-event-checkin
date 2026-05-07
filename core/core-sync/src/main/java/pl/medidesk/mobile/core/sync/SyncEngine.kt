package pl.medidesk.mobile.core.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.WalkinDao
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.model.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineCheckinDao: OfflineCheckinDao,
    private val walkinDao: WalkinDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)

    val syncState: StateFlow<SyncState> = combine(
        offlineCheckinDao.getUnsyncedCountFlow(),
        walkinDao.getPendingCountFlow()
    ) { pendingCheckins, pendingWalkins ->
        SyncState(
            status = if (pendingCheckins > 0 || pendingWalkins > 0) SyncStatus.IDLE else SyncStatus.SUCCESS,
            pendingCheckins = pendingCheckins,
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

    fun stopPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME_PERIODIC)
    }
}
