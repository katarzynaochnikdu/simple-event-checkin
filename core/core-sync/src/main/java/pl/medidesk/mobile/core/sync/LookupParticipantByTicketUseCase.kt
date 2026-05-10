package pl.medidesk.mobile.core.sync

import pl.medidesk.mobile.core.database.dao.ParticipantDao
import javax.inject.Inject

/**
 * Lokalne wyszukanie uczestnika po ticket_id (z QR kodu) — bez wywołania API.
 * Używane do prezentacji ekranu potwierdzenia check-in PRZED wysłaniem żądania.
 *
 * Brak wpisu w lokalnej bazie nie oznacza, że uczestnik nie istnieje na serwerze
 * (np. dodany po ostatnim sync'u) — UI powinien wtedy pokazać dialog potwierdzenia
 * z generycznym opisem zamiast blokować check-in.
 */
class LookupParticipantByTicketUseCase @Inject constructor(
    private val participantDao: ParticipantDao
) {
    suspend operator fun invoke(ticketId: String): LookupResult {
        val p = participantDao.findByAnyTicketId(ticketId) ?: return LookupResult.NotFound
        val name = listOfNotNull(p.firstName, p.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { p.email.orEmpty() }
        return LookupResult.Found(
            ticketId = ticketId,
            participantName = name.ifBlank { "Uczestnik" },
            ticketName = p.ticketName.orEmpty(),
            company = p.company.orEmpty(),
            alreadyCheckedIn = p.checkedInAt != null
        )
    }
}

sealed class LookupResult {
    data object NotFound : LookupResult()
    data class Found(
        val ticketId: String,
        val participantName: String,
        val ticketName: String,
        val company: String,
        val alreadyCheckedIn: Boolean
    ) : LookupResult()
}
