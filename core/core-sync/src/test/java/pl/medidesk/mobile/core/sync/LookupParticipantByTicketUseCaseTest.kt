package pl.medidesk.mobile.core.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.medidesk.mobile.core.database.dao.ParticipantDao
import pl.medidesk.mobile.core.database.entities.ParticipantEntity
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.ParticipantDto
import pl.medidesk.mobile.core.network.dto.ParticipantsResponse
import retrofit2.Response

class LookupParticipantByTicketUseCaseTest {
    @Test
    fun test_current_ticket_number_matches_server_participant() = runTest {
        // Arrange
        val dao = mockk<ParticipantDao>()
        val api = mockk<MobileApiService>()
        coEvery { dao.findByTicketAndEvent(CURRENT_TICKET, EVENT_ID) } returns null
        coEvery { dao.findByAnyTicketId(CURRENT_TICKET) } returns null
        coEvery { api.getParticipants(EVENT_ID, since = null) } returns Response.success(
            ParticipantsResponse(EVENT_ID, 1, listOf(serverParticipant(ticketNumber = CURRENT_TICKET)))
        )

        // Act
        val result = LookupParticipantByTicketUseCase(dao, api)(CURRENT_TICKET, EVENT_ID)

        // Assert
        assertTrue(result is LookupResult.Found)
        coVerify(exactly = 1) { api.getParticipants(EVENT_ID, since = null) }
    }

    @Test
    fun test_current_ticket_id_matches_local_participant_without_server_call() = runTest {
        // Arrange
        val dao = mockk<ParticipantDao>()
        val api = mockk<MobileApiService>()
        coEvery { dao.findByTicketAndEvent(CURRENT_TICKET, EVENT_ID) } returns localParticipant(EVENT_ID)

        // Act
        val result = LookupParticipantByTicketUseCase(dao, api)(CURRENT_TICKET, EVENT_ID)

        // Assert
        assertTrue(result is LookupResult.Found)
        coVerify(exactly = 0) { api.getParticipants(any(), any()) }
    }

    @Test
    fun test_historical_only_identifier_does_not_match_server_response() = runTest {
        // Arrange
        val dao = mockk<ParticipantDao>()
        val api = mockk<MobileApiService>()
        coEvery { dao.findByTicketAndEvent(HISTORICAL_TICKET, EVENT_ID) } returns null
        coEvery { dao.findByAnyTicketId(HISTORICAL_TICKET) } returns null
        coEvery { api.getParticipants(EVENT_ID, since = null) } returns Response.success(
            ParticipantsResponse(EVENT_ID, 1, listOf(serverParticipant(ticketId = CURRENT_TICKET)))
        )

        // Act
        val result = LookupParticipantByTicketUseCase(dao, api)(HISTORICAL_TICKET, EVENT_ID)

        // Assert
        assertEquals(LookupResult.NotFound, result)
        coVerify(exactly = 0) { api.syncCheckins(any()) }
    }

    private fun serverParticipant(ticketId: String? = null, ticketNumber: String? = null) = ParticipantDto(
        id = 42,
        ticketId = ticketId,
        ticketNumber = ticketNumber,
        firstName = "Test",
        lastName = "Participant",
        email = null,
        company = null,
        ticketClassId = null,
        ticketName = "Standard",
        status = "confirmed",
        attendanceStatus = null,
        eventOrderId = null,
        checkedInAt = null
    )

    private fun localParticipant(eventId: String) = ParticipantEntity(
        id = 42,
        ticketId = CURRENT_TICKET,
        ticketNumber = null,
        backstageTicketId = HISTORICAL_TICKET,
        firstName = "Test",
        lastName = "Participant",
        email = null,
        phone = null,
        company = null,
        ticketClassId = null,
        ticketName = "Standard",
        status = "confirmed",
        attendanceStatus = null,
        eventOrderId = null,
        eventId = eventId,
        checkedInAt = null
    )

    private companion object {
        const val CURRENT_TICKET = "ticket-current-001"
        const val HISTORICAL_TICKET = "historic-only-001"
        const val EVENT_ID = "event-test-001"
    }
}
