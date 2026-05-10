package pl.medidesk.mobile.core.sync

import android.util.Log
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.network.MobileApiService
import javax.inject.Inject

/**
 * Wyszukanie uczestnika po ticket_id (z QR kodu) — bez wywołania check-in.
 * Używane do prezentacji ekranu potwierdzenia check-in PRZED wysłaniem żądania.
 *
 * Strategia:
 *  1. Najpierw lokalna baza SQLite (instant).
 *  2. Jeśli miss (np. operator wszedł od razu na skaner i SyncWorker jeszcze nie
 *     pobrał uczestników) — fallback na backend `/events/{eventId}/participants`
 *     z filtrowaniem po stronie klienta (event może mieć kilkadziesiąt-kilkaset
 *     uczestników, więc to OK na rzadkie miss-y).
 *  3. Jeśli serwer też nie zwraca danych (np. brak sieci) — zwracamy NotFound.
 *     UI pokazuje wtedy generyczny dialog potwierdzenia, a faktyczny check-in
 *     i tak idzie do backendu — duplikat / not_found będzie obsłużony tam.
 */
class LookupParticipantByTicketUseCase @Inject constructor(
    private val participantDao: ParticipantDao,
    private val apiService: MobileApiService
) {
    suspend operator fun invoke(ticketId: String, eventId: String): LookupResult {
        // 1. Lokalna baza
        val local = participantDao.findByAnyTicketId(ticketId)
        if (local != null) {
            val name = listOfNotNull(local.firstName, local.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { local.email.orEmpty() }
                .ifBlank { "Uczestnik" }
            return LookupResult.Found(
                ticketId = ticketId,
                participantName = name,
                ticketName = local.ticketName.orEmpty(),
                company = local.company.orEmpty(),
                email = local.email.orEmpty(),
                alreadyCheckedIn = local.checkedInAt != null
            )
        }

        // 2. Fallback na serwer
        return try {
            val response = apiService.getParticipants(eventId, since = null)
            if (!response.isSuccessful) {
                Log.w("LookupParticipantByTicket", "Server lookup failed: code=${response.code()}")
                return LookupResult.NotFound
            }
            val participants = response.body()?.participants.orEmpty()
            val match = participants.firstOrNull {
                it.ticketId == ticketId || it.backstageTicketId == ticketId
            } ?: return LookupResult.NotFound

            val name = listOfNotNull(match.firstName, match.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { match.email.orEmpty() }
                .ifBlank { "Uczestnik" }

            LookupResult.Found(
                ticketId = ticketId,
                participantName = name,
                ticketName = match.ticketName.orEmpty(),
                company = match.company.orEmpty(),
                email = match.email.orEmpty(),
                alreadyCheckedIn = match.checkedInAt != null
            )
        } catch (e: Exception) {
            Log.w("LookupParticipantByTicket", "Server lookup error: ${e.message}")
            LookupResult.NotFound
        }
    }
}

sealed class LookupResult {
    data object NotFound : LookupResult()
    data class Found(
        val ticketId: String,
        val participantName: String,
        val ticketName: String,
        val company: String,
        val email: String,
        val alreadyCheckedIn: Boolean
    ) : LookupResult()
}
