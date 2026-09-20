package pl.medidesk.mobile.core.sync

import android.util.Log
import pl.medidesk.mobile.core.model.CheckinResult
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.sync.BuildConfig
import pl.medidesk.mobile.core.network.dto.CheckinRequest
import pl.medidesk.mobile.core.database.dao.OfflineCheckinDao
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.database.entities.OfflineCheckinEntity
import pl.medidesk.mobile.core.database.entities.ParticipantEntity
import pl.medidesk.mobile.core.mappers.toDomainList
import pl.medidesk.mobile.core.mappers.toPendingTicketNames
import pl.medidesk.mobile.core.model.ParticipantSummary
import pl.medidesk.mobile.core.model.decodeTicketEntitlements
import pl.medidesk.mobile.core.network.dto.ParticipantSummaryDto
import pl.medidesk.mobile.core.network.dto.PendingTicketDto
import pl.medidesk.mobile.core.network.dto.TicketEntitlementDto
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
        if (BuildConfig.DEBUG) Log.d("CheckinUseCase", "Checking in ticket: $ticketId for event: $eventId")

        return try {
            val response = apiService.checkin(CheckinRequest(ticketId = ticketId, eventId = eventId, scannedAt = scannedAt))
            val body = response.body()
            if (BuildConfig.DEBUG) Log.d("CheckinUseCase", "Server response: code=${response.code()}, success=${body?.success}, error=${body?.error}")
            if (response.isSuccessful && body != null) {
                val checkedInAt = body.checkedInAt
                if (body.success && checkedInAt != null) {
                    participantDao.markCheckedIn(ticketId, checkedInAt)
                    syncEngine.notifyParticipantChanged(
                        ParticipantStatusChange(
                            ticketId = ticketId,
                            participantId = body.participant?.id,
                            isCheckedIn = true,
                            checkedInAt = checkedInAt
                        )
                    )
                }
                val local = participantDao.findByAnyTicketId(ticketId)
                CheckinResult(
                    success = body.success,
                    alreadyCheckedIn = body.alreadyCheckedIn,
                    checkedInAt = body.checkedInAt,
                    participant = body.participant.toSummary(
                        fallbackTickets = body.tickets,
                        pendingTickets = body.pendingTickets,
                        local = local
                    ),
                    error = body.error,
                    isOffline = false,
                    ticketNumber = body.participant?.ticketNumber
                        ?: local?.ticketNumber
                        ?: ticketId
                )
            } else {
                if (BuildConfig.DEBUG) {
                    val errorBody = response.errorBody()?.string()
                    Log.w("CheckinUseCase", "Online checkin failed: code=${response.code()}, body=$errorBody — trying local")
                } else {
                    Log.w("CheckinUseCase", "Online checkin failed: code=${response.code()} — trying local")
                }
                localCheckin(ticketId, eventId, scannedAt)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("CheckinUseCase", "Online checkin error: ${e.message} — trying local", e)
            } else {
                Log.e("CheckinUseCase", "Online checkin error — trying local")
            }
            localCheckin(ticketId, eventId, scannedAt)
        }
    }

    private suspend fun localCheckin(ticketId: String, eventId: String, scannedAt: String): CheckinResult {
        if (BuildConfig.DEBUG) Log.d("CheckinUseCase", "Falling back to local/offline checkin for ticket: $ticketId")
        val local = participantDao.findByAnyTicketId(ticketId)
        return if (local != null) {
            if (local.checkedInAt != null) {
                CheckinResult(
                    success = true,
                    alreadyCheckedIn = true,
                    checkedInAt = local.checkedInAt,
                    participant = local.toSummary(),
                    isOffline = true,
                    ticketNumber = local.ticketNumber ?: ticketId
                )
            } else {
                offlineCheckinDao.insert(OfflineCheckinEntity(ticketId = ticketId, eventId = eventId, scannedAt = scannedAt))
                participantDao.markCheckedIn(ticketId, scannedAt)
                syncEngine.triggerImmediateSync(eventId)
                syncEngine.notifyParticipantChanged(
                    ParticipantStatusChange(
                        ticketId = ticketId,
                        participantId = local.id,
                        isCheckedIn = true,
                        checkedInAt = scannedAt
                    )
                )

                CheckinResult(
                    success = true,
                    alreadyCheckedIn = false,
                    checkedInAt = scannedAt,
                    participant = local.toSummary(),
                    isOffline = true,
                    ticketNumber = local.ticketNumber ?: ticketId
                )
            }
        } else {
            CheckinResult(success = false, error = "not_found", isOffline = true)
        }
    }
}

private fun ParticipantSummaryDto?.toSummary(
    fallbackTickets: List<TicketEntitlementDto>?,
    pendingTickets: List<PendingTicketDto>?,
    local: ParticipantEntity?
): ParticipantSummary? {
    val dto = this ?: return local?.toSummary()
    val fromResponse = fallbackTickets.toDomainList()
    val tickets = fromResponse.ifEmpty { decodeTicketEntitlements(local?.ticketsJson) }
    return ParticipantSummary(
        id = dto.id,
        firstName = dto.firstName.orEmpty(),
        lastName = dto.lastName.orEmpty(),
        email = dto.email.orEmpty(),
        company = dto.company.orEmpty(),
        ticketName = dto.ticketName.orEmpty().ifBlank { local?.ticketName.orEmpty() },
        ticketClassId = dto.ticketClassId.orEmpty(),
        tickets = tickets,
        ticketNumber = dto.ticketNumber.orEmpty().ifBlank { local?.ticketNumber.orEmpty() },
        // Bilety w toku są tylko informacją na ekranie — check-in ich nie obejmuje.
        pendingTicketNames = pendingTickets.toPendingTicketNames()
    )
}

private fun ParticipantEntity.toSummary(): ParticipantSummary = ParticipantSummary(
    id = id,
    firstName = firstName.orEmpty(),
    lastName = lastName.orEmpty(),
    email = email.orEmpty(),
    company = company.orEmpty(),
    ticketName = ticketName.orEmpty(),
    ticketClassId = ticketClassId.orEmpty(),
    tickets = decodeTicketEntitlements(ticketsJson),
    ticketNumber = ticketNumber.orEmpty()
)
