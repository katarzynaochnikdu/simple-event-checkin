package pl.medidesk.mobile.core.sync

import android.util.Log
import pl.medidesk.mobile.core.model.CheckinResult
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.CheckinRequest
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.database.entities.OfflineCheckinEntity
import pl.medidesk.mobile.core.model.ParticipantSummary
import java.time.Instant
import javax.inject.Inject

class CheckinUseCase @Inject constructor(
    private val apiService: MobileApiService,
    private val participantDao: ParticipantDao,
    private val offlineCheckinDao: OfflineCheckinDao,
    private val syncEngine: SyncEngine
) {
    suspend operator fun invoke(ticketId: String, eventId: String): CheckinResult {
        val scannedAt = Instant.now().toString()
        Log.d("CheckinUseCase", "Checking in ticket: $ticketId for event: $eventId")

        return try {
            val response = apiService.checkin(CheckinRequest(ticketId = ticketId, eventId = eventId, scannedAt = scannedAt))
            val body = response.body()
            Log.d("CheckinUseCase", "Server response: code=${response.code()}, success=${body?.success}, error=${body?.error}")
            if (response.isSuccessful && body != null) {
                val checkedInAt = body.checkedInAt
                if (body.success && checkedInAt != null) {
                    participantDao.markCheckedIn(ticketId, checkedInAt)
                }
                CheckinResult(
                    success = body.success,
                    alreadyCheckedIn = body.alreadyCheckedIn,
                    checkedInAt = body.checkedInAt,
                    participant = body.participant?.let {
                        ParticipantSummary(it.id, it.firstName, it.lastName, it.email, it.company, it.ticketName, it.ticketClassId)
                    },
                    error = body.error,
                    isOffline = false
                )
            } else {
                val errorBody = response.errorBody()?.string()
                Log.w("CheckinUseCase", "Online checkin failed: code=${response.code()}, body=$errorBody — trying local")
                localCheckin(ticketId, eventId, scannedAt)
            }
        } catch (e: Exception) {
            Log.e("CheckinUseCase", "Online checkin error: ${e.message} — trying local", e)
            localCheckin(ticketId, eventId, scannedAt)
        }
    }

    private suspend fun localCheckin(ticketId: String, eventId: String, scannedAt: String): CheckinResult {
        Log.d("CheckinUseCase", "Falling back to local/offline checkin for ticket: $ticketId")
        val local = participantDao.findByAnyTicketId(ticketId)
        return if (local != null) {
            if (local.checkedInAt != null) {
                CheckinResult(success = true, alreadyCheckedIn = true, checkedInAt = local.checkedInAt,
                    participant = ParticipantSummary(local.id, local.firstName ?: "", local.lastName ?: "",
                        local.email ?: "", local.company ?: "", local.ticketName ?: "", local.ticketClassId ?: ""),
                    isOffline = true)
            } else {
                offlineCheckinDao.insert(OfflineCheckinEntity(ticketId = ticketId, eventId = eventId, scannedAt = scannedAt))
                participantDao.markCheckedIn(ticketId, scannedAt)
                syncEngine.triggerImmediateSync(eventId)

                CheckinResult(success = true, alreadyCheckedIn = false, checkedInAt = scannedAt,
                    participant = ParticipantSummary(local.id, local.firstName ?: "", local.lastName ?: "",
                        local.email ?: "", local.company ?: "", local.ticketName ?: "", local.ticketClassId ?: ""),
                    isOffline = true)
            }
        } else {
            CheckinResult(success = false, error = "not_found", isOffline = true)
        }
    }
}
