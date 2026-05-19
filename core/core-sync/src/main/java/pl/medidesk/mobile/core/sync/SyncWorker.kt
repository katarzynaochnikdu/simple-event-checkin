package pl.medidesk.mobile.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.database.dao.SyncMetadataDao
import pl.medidesk.mobile.core.database.dao.WalkinDao
import pl.medidesk.mobile.core.database.entities.SyncMetadataEntity
import pl.medidesk.mobile.core.mappers.toEntity
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.analytics.Analytics
import pl.medidesk.mobile.core.analytics.AnalyticsEvent
import pl.medidesk.mobile.core.sync.BuildConfig
import pl.medidesk.mobile.core.network.dto.CheckinSyncItem
import pl.medidesk.mobile.core.network.dto.CheckinSyncRequest
import pl.medidesk.mobile.core.network.dto.WalkinBatchRequest
import pl.medidesk.mobile.core.network.dto.WalkinRequest
import java.time.Instant

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: MobileApiService,
    private val participantDao: ParticipantDao,
    private val offlineCheckinDao: OfflineCheckinDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val walkinDao: WalkinDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_FORCE_FULL_PULL = "force_full_pull"
        const val WORK_NAME_PERIODIC = "md_sync_periodic"
        const val WORK_NAME_IMMEDIATE = "md_sync_immediate"
        private const val TAG = "SyncWorker"

        fun periodicWorkRequest(eventId: String): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(5, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_EVENT_ID to eventId, KEY_FORCE_FULL_PULL to false))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
                .build()

        fun immediateWorkRequest(eventId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_EVENT_ID to eventId, KEY_FORCE_FULL_PULL to true))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
    }

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        val forceFullPull = inputData.getBoolean(KEY_FORCE_FULL_PULL, false)
        val startMs = System.currentTimeMillis()

        var hasError = false
        var pushedCount = 0
        var pulledCount = 0

        // 1. Push Checkins FIRST (so server knows about new checkins)
        try {
            pushedCount = pushCheckins(eventId)
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing checkins", e)
            hasError = true
        }

        // 2. Pull Participants (to get updated state from server)
        try {
            pulledCount = pullParticipants(eventId, forceFullPull)
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling participants", e)
            hasError = true
        }

        // 3. Push Walkins
        try {
            pushWalkins()
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing walkins", e)
            hasError = true
        }

        val durationMs = System.currentTimeMillis() - startMs
        val pendingCount = offlineCheckinDao.getUnsyncedCount()

        return if (hasError) {
            Analytics.capture(
                AnalyticsEvent.SYNC_FAILED,
                mapOf(
                    AnalyticsEvent.Props.ERROR_TYPE to "worker_error",
                    AnalyticsEvent.Props.PENDING_COUNT to pendingCount
                )
            )
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } else {
            Analytics.capture(
                AnalyticsEvent.SYNC_COMPLETED,
                mapOf(
                    AnalyticsEvent.Props.PUSHED_COUNT to pushedCount,
                    AnalyticsEvent.Props.PULLED_COUNT to pulledCount,
                    AnalyticsEvent.Props.DURATION_MS to durationMs,
                    AnalyticsEvent.Props.FORCE_FULL to forceFullPull
                )
            )
            Result.success()
        }
    }

    private suspend fun pullParticipants(eventId: String, forceFullPull: Boolean = false): Int {
        val meta = syncMetadataDao.get(eventId)
        // Force full pull (immediate sync triggered by UI resume / after checkin):
        // pass since=null so backend returns ALL participants and we replaceAll locally —
        // ensures deletions and uncheck-ins from the web panel propagate to mobile cache.
        val since = if (forceFullPull) null else meta?.lastParticipantsSync

        if (BuildConfig.DEBUG) Log.d(TAG, "Pulling participants for $eventId since=$since (forceFullPull=$forceFullPull)")
        val response = apiService.getParticipants(eventId, since)
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to fetch participants: ${response.code()}")
            throw Exception("Network error")
        }

        val body = response.body() ?: return 0
        if (BuildConfig.DEBUG) Log.d(TAG, "Fetched ${body.participants.size} participants")
        
        val entities = body.participants.map { dto -> dto.toEntity(eventId) }

        if (since == null) {
            participantDao.replaceAll(eventId, entities)
        } else if (entities.isNotEmpty()) {
            participantDao.insertAll(entities)
        }

        val now = Instant.now().toString()
        if (meta == null) {
            syncMetadataDao.upsert(SyncMetadataEntity(eventId = eventId, lastParticipantsSync = now))
        } else {
            syncMetadataDao.updateParticipantsSync(eventId, now)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Successfully updated local database with ${entities.size} participants")
        return entities.size
    }

    private suspend fun pushCheckins(eventId: String): Int {
        val unsynced = offlineCheckinDao.getUnsynced().filter { it.eventId == eventId }
        if (unsynced.isEmpty()) return 0

        if (BuildConfig.DEBUG) Log.d(TAG, "Pushing ${unsynced.size} checkins to server")
        val items = unsynced.mapNotNull { e ->
            val tid = e.ticketId ?: e.backstageTicketId ?: return@mapNotNull null
            CheckinSyncItem(
                ticketId = tid,
                eventId = e.eventId,
                scannedAt = e.scannedAt,
                deviceId = e.deviceId,
                action = e.action
            )
        }

        if (items.isEmpty()) return 0

        val response = apiService.syncCheckins(CheckinSyncRequest(items))
        if (response.isSuccessful) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Successfully synced ${items.size} checkins")
            offlineCheckinDao.markAllSyncedForEvent(eventId)
            syncMetadataDao.updateCheckinPush(eventId, Instant.now().toString())
            return items.size
        } else {
             if (BuildConfig.DEBUG) {
                 Log.e(TAG, "Failed to push checkins: ${response.code()} ${response.errorBody()?.string()}")
             } else {
                 Log.e(TAG, "Failed to push checkins: ${response.code()}")
             }
             throw Exception("Checkin sync failed")
        }
    }

    private suspend fun pushWalkins() {
        val pending = walkinDao.getPending()
        if (pending.isEmpty()) return

        val items = pending.map { e ->
            WalkinRequest(
                eventId = e.eventId,
                firstName = e.firstName,
                lastName = e.lastName,
                walkInCode = e.walkInCode,
                email = e.email,
                phone = e.phone,
                company = e.company,
                ticketClassId = e.ticketClassId,
                notes = e.notes,
                checkedInAt = e.checkedInAt
            )
        }

        val response = apiService.syncWalkins(WalkinBatchRequest(items))
        if (response.isSuccessful) {
            walkinDao.markAllSynced()
        } else {
            Log.e(TAG, "Failed to push walkins: ${response.code()}")
        }
    }
}
