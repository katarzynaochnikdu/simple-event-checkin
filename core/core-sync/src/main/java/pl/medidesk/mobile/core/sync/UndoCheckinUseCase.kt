package pl.medidesk.mobile.core.sync

import android.util.Log
import pl.medidesk.mobile.core.model.CheckinResult
import pl.medidesk.mobile.core.model.ParticipantSummary
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.UndoCheckinRequest
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import javax.inject.Inject

class UndoCheckinUseCase @Inject constructor(
    private val apiService: MobileApiService,
    private val participantDao: ParticipantDao,
    private val offlineCheckinDao: OfflineCheckinDao
) {
    suspend operator fun invoke(ticketId: String, eventId: String): CheckinResult {
        Log.d("UndoCheckinUseCase", "Undoing checkin for ticket: $ticketId, event: $eventId")

        return try {
            val response = apiService.undoCheckin(UndoCheckinRequest(ticketId = ticketId, eventId = eventId))
            val body = response.body()
            Log.d("UndoCheckinUseCase", "Server response: code=${response.code()}, success=${body?.success}")

            if (response.isSuccessful && body != null && body.success) {
                participantDao.markCheckedOut(ticketId)
                offlineCheckinDao.deleteUnsyncedCheckin(ticketId)
                CheckinResult(
                    success = true,
                    participant = body.participant?.let {
                        ParticipantSummary(it.id, it.firstName, it.lastName, it.email, it.company, it.ticketName, it.ticketClassId)
                    },
                    isOffline = false
                )
            } else {
                val error = body?.error ?: "undo_failed"
                Log.w("UndoCheckinUseCase", "Server undo failed: $error")
                CheckinResult(success = false, error = error, isOffline = false)
            }
        } catch (e: Exception) {
            Log.e("UndoCheckinUseCase", "Online undo failed: ${e.message} — reverting locally", e)
            localUndo(ticketId)
        }
    }

    private suspend fun localUndo(ticketId: String): CheckinResult {
        val local = participantDao.findByAnyTicketId(ticketId) ?: return CheckinResult(success = false, error = "not_found", isOffline = true)

        if (local.checkedInAt == null) {
            return CheckinResult(success = false, error = "not_checked_in", isOffline = true)
        }

        participantDao.markCheckedOut(ticketId)
        offlineCheckinDao.deleteUnsyncedCheckin(ticketId)

        return CheckinResult(
            success = true,
            participant = ParticipantSummary(
                local.id, local.firstName ?: "", local.lastName ?: "",
                local.email ?: "", local.company ?: "", local.ticketName ?: "", local.ticketClassId ?: ""
            ),
            isOffline = true
        )
    }
}
