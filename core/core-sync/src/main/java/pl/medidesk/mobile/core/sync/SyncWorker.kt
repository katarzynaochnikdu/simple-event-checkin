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
import pl.medidesk.mobile.core.database.entities.ParticipantEntity
import pl.medidesk.mobile.core.database.entities.SyncMetadataEntity
import pl.medidesk.mobile.core.network.MobileApiService
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
        const val WORK_NAME_PERIODIC = "md_sync_periodic"
        const val WORK_NAME_IMMEDIATE = "md_sync_immediate"
        private const val TAG = "SyncWorker"

        fun periodicWorkRequest(eventId: String): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(5, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_EVENT_ID to eventId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
                .build()

        fun immediateWorkRequest(eventId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_EVENT_ID to eventId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
    }

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        
        var hasError = false

        // 1. Push Checkins FIRST (so server knows about new checkins)
        try {
            pushCheckins(eventId)
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing checkins", e)
            hasError = true
        }

        // 2. Pull Participants (to get updated state from server)
        try {
            pullParticipants(eventId)
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

        return if (hasError) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } else {
            Result.success()
        }
    }

    private suspend fun pullParticipants(eventId: String) {
        val meta = syncMetadataDao.get(eventId)
        val since = meta?.lastParticipantsSync

        Log.d(TAG, "Pulling participants for $eventId since $since")
        val response = apiService.getParticipants(eventId, since)
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to fetch participants: ${response.code()}")
            throw Exception("Network error")
        }

        val body = response.body() ?: return
        Log.d(TAG, "Fetched ${body.participants.size} participants")
        
        val entities = body.participants.map { dto ->
            ParticipantEntity(
                id = dto.id,
                ticketId = dto.ticketId,
                ticketNumber = dto.ticketNumber,
                backstageTicketId = dto.backstageTicketId,
                firstName = dto.firstName,
                lastName = dto.lastName,
                email = dto.email,
                phone = dto.phone,
                company = dto.company,
                ticketClassId = dto.ticketClassId,
                ticketName = dto.ticketName,
                status = dto.status,
                attendanceStatus = dto.attendanceStatus,
                eventOrderId = dto.eventOrderId,
                eventId = eventId,
                checkedInAt = dto.checkedInAt,
                orderStatus = dto.orderStatus,
                isWalkin = dto.isWalkin,
                tags = dto.tags?.joinToString(","),
                buyerName = dto.buyerName,
                buyerEmail = dto.buyerEmail,
                paymentMethod = dto.paymentMethod,
                purchaserNip = dto.purchaserNip,
                purchaserCompany = dto.purchaserCompany,
                orderParticipantsTotal = dto.orderParticipantsTotal,
                orderParticipantsCheckedIn = dto.orderParticipantsCheckedIn
            )
        }

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
        Log.d(TAG, "Successfully updated local database with ${entities.size} participants")
    }

    private suspend fun pushCheckins(eventId: String) {
        val unsynced = offlineCheckinDao.getUnsynced().filter { it.eventId == eventId }
        if (unsynced.isEmpty()) return

        Log.d(TAG, "Pushing ${unsynced.size} checkins to server")
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

        if (items.isEmpty()) return

        val response = apiService.syncCheckins(CheckinSyncRequest(items))
        if (response.isSuccessful) {
            Log.d(TAG, "Successfully synced ${items.size} checkins")
            offlineCheckinDao.markAllSyncedForEvent(eventId)
            syncMetadataDao.updateCheckinPush(eventId, Instant.now().toString())
        } else {
             Log.e(TAG, "Failed to push checkins: ${response.code()} ${response.errorBody()?.string()}")
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
