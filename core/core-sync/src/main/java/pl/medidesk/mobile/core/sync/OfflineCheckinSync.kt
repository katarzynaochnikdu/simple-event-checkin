package pl.medidesk.mobile.core.sync

import pl.medidesk.mobile.core.database.entities.OfflineCheckinEntity

/** Keep historical Room rows stored while selecting only current, event-scoped tickets for upload. */
internal fun currentOfflineCheckinsForEvent(
    entries: List<OfflineCheckinEntity>,
    eventId: String
): List<OfflineCheckinEntity> = entries.filter { entry ->
    entry.eventId == eventId && !entry.ticketId.isNullOrBlank()
}
