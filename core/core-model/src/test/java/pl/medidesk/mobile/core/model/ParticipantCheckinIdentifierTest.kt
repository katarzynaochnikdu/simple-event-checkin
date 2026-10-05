package pl.medidesk.mobile.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParticipantCheckinIdentifierTest {

    @Test
    fun currentCheckinIdentifier_prefersTicketId() {
        val participant = participant(ticketId = "ticket-id", ticketNumber = "ticket-number")

        assertEquals("ticket-id", participant.currentCheckinIdentifier)
    }

    @Test
    fun currentCheckinIdentifier_fallsBackToTicketNumber() {
        val participant = participant(ticketId = null, ticketNumber = "ticket-number")

        assertEquals("ticket-number", participant.currentCheckinIdentifier)
    }

    @Test
    fun currentCheckinIdentifier_returnsNullWhenBothMissing() {
        val participant = participant(ticketId = null, ticketNumber = null)

        assertNull(participant.currentCheckinIdentifier)
    }

    private fun participant(ticketId: String?, ticketNumber: String?) = Participant(
        id = 1L,
        ticketId = ticketId,
        ticketNumber = ticketNumber,
        firstName = null,
        lastName = null,
        email = null,
        company = null,
        ticketClassId = null,
        ticketName = null,
        status = null,
        attendanceStatus = null,
        eventOrderId = null,
        eventId = "event-id",
        checkedInAt = null
    )
}
