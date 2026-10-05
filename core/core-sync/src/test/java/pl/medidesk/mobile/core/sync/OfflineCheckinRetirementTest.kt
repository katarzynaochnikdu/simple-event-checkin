package pl.medidesk.mobile.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.medidesk.mobile.core.database.entities.OfflineCheckinEntity

class OfflineCheckinRetirementTest {
    @Test
    fun test_only_current_ticket_identifiers_for_selected_event_enter_offline_upload() {
        // Arrange
        val historical = entry(1, "event-test", null, "old-ticket")
        val current = entry(2, "event-test", "current-ticket", "old-ticket")
        val otherEvent = entry(3, "event-other", "other-ticket", null)

        // Act
        val selected = currentOfflineCheckinsForEvent(listOf(historical, current, otherEvent), "event-test")

        // Assert
        assertEquals(listOf(current), selected)
        assertEquals(null, historical.ticketId)
    }

    private fun entry(id: Long, eventId: String, ticketId: String?, historicalId: String?) =
        OfflineCheckinEntity(
            id = id,
            eventId = eventId,
            ticketId = ticketId,
            backstageTicketId = historicalId,
            scannedAt = "2026-09-29T10:00:00Z"
        )
}
