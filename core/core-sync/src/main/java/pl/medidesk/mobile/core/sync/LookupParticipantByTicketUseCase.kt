package pl.medidesk.mobile.core.sync

import android.util.Log
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.mappers.toDomainList
import pl.medidesk.mobile.core.mappers.toPendingTicketNames
import pl.medidesk.mobile.core.model.TicketEntitlement
import pl.medidesk.mobile.core.model.decodeTicketEntitlements
import pl.medidesk.mobile.core.model.entitlementDisplayNames
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.sync.BuildConfig
import javax.inject.Inject

/**
 * Wyszukanie uczestnika po ticket_id (z QR kodu) — bez wywołania check-in.
 * Używane do prezentacji ekranu potwierdzenia check-in PRZED wysłaniem żądania.
 *
 * Strategia (per scope: AKTUALNE wydarzenie):
 *  1. Lokalna baza filtrowana po (ticketId, eventId) — instant.
 *  2. Cross-event check: jeśli ticket istnieje w lokalnej bazie ale dla INNEGO
 *     wydarzenia → zwraca WrongEvent (operator zeskanował bilet spoza tego eventu).
 *  3. Server fallback `/events/{eventId}/participants` (filtruje po stronie backendu).
 *  4. Jeśli serwer też nic nie zwraca — NotFound.
 *
 * Backend `mobile_checkin` i tak waliduje event_id (pg_storage `WHERE o.event_id = %s`),
 * więc cross-event check-in nigdy nie wejdzie do bazy. Ten use case daje
 * prawidłowe komunikaty UX zanim user klikinie "Tak, Check-In" niepotrzebnie.
 */
class LookupParticipantByTicketUseCase @Inject constructor(
    private val participantDao: ParticipantDao,
    private val apiService: MobileApiService
) {
    suspend operator fun invoke(ticketId: String, eventId: String): LookupResult {
        // 1. Lokalna baza — strict event match
        val localThis = participantDao.findByTicketAndEvent(ticketId, eventId)
        if (localThis != null) {
            return found(
                ticketId,
                localThis.firstName,
                localThis.lastName,
                localThis.email,
                localThis.ticketName,
                localThis.company,
                localThis.checkedInAt != null,
                localThis.orderStatus,
                localThis.ticketNumber,
                decodeTicketEntitlements(localThis.ticketsJson)
            )
        }

        // 2. Cross-event check — może mamy bilet, ale dla innego wydarzenia
        val localOther = participantDao.findByAnyTicketId(ticketId)
        if (localOther != null && localOther.eventId != eventId) {
            val name = composeName(localOther.firstName, localOther.lastName, localOther.email)
            return LookupResult.WrongEvent(
                ticketId = ticketId,
                participantName = name,
                otherEventId = localOther.eventId
            )
        }

        // 3. Server fallback (filtrowany po eventId po stronie backendu)
        return try {
            val response = apiService.getParticipants(eventId, since = null)
            if (!response.isSuccessful) {
                // WO-MOB-034 (F2B-005): DEBUG-guard — bez logu w release (higiena MOB-7).
                if (BuildConfig.DEBUG) {
                    Log.w("LookupParticipantByTicket", "Server lookup failed: code=${response.code()}")
                }
                return LookupResult.NotFound
            }
            val match = response.body()?.participants.orEmpty().firstOrNull {
                it.ticketNumber == ticketId
                    || it.ticketId == ticketId
                    || it.backstageTicketId == ticketId
            } ?: return LookupResult.NotFound

            found(
                ticketId,
                match.firstName,
                match.lastName,
                match.email,
                match.ticketName,
                match.company,
                match.checkedInAt != null,
                match.orderStatus,
                match.ticketNumber,
                match.tickets.toDomainList(),
                // Bilety czekające na dopłatę — tylko informacja dla obsługi.
                // Lokalny cache ich nie przechowuje (brak kolumny w bazie),
                // więc widać je na tej ścieżce oraz w odpowiedzi check-inu.
                match.pendingTickets.toPendingTicketNames()
            )
        } catch (e: Exception) {
            // WO-MOB-034 (F2B-005): DEBUG-guard — e.message poza release (higiena MOB-7).
            if (BuildConfig.DEBUG) {
                Log.w("LookupParticipantByTicket", "Server lookup error: ${e.message}")
            }
            LookupResult.NotFound
        }
    }

    private fun composeName(first: String?, last: String?, email: String?): String =
        listOfNotNull(first, last)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { email.orEmpty() }
            .ifBlank { "Uczestnik" }

    private fun found(
        ticketId: String, first: String?, last: String?, email: String?,
        ticketName: String?, company: String?, alreadyCheckedIn: Boolean,
        orderStatus: String? = null,
        ticketNumber: String? = null,
        tickets: List<TicketEntitlement> = emptyList(),
        pendingTicketNames: List<String> = emptyList()
    ): LookupResult.Found = LookupResult.Found(
        ticketId = ticketId,
        participantName = composeName(first, last, email),
        ticketName = ticketName.orEmpty(),
        company = company.orEmpty(),
        email = email.orEmpty(),
        alreadyCheckedIn = alreadyCheckedIn,
        orderStatus = orderStatus,
        ticketNumber = ticketNumber.orEmpty().ifBlank { ticketId },
        ticketNames = entitlementDisplayNames(tickets, ticketName),
        pendingTicketNames = pendingTicketNames
    )
}

sealed class LookupResult {
    data object NotFound : LookupResult()

    /** Bilet znaleziony lokalnie ale przypisany do INNEGO wydarzenia. */
    data class WrongEvent(
        val ticketId: String,
        val participantName: String,
        val otherEventId: String
    ) : LookupResult()

    data class Found(
        val ticketId: String,
        val participantName: String,
        val ticketName: String,
        val company: String,
        val email: String,
        val alreadyCheckedIn: Boolean,
        val orderStatus: String? = null,
        val ticketNumber: String = "",
        val ticketNames: List<String> = emptyList(),
        /** Bilety czekające na opłacenie — tylko do pokazania, nie do wpuszczenia. */
        val pendingTicketNames: List<String> = emptyList()
    ) : LookupResult()
}
