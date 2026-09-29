package pl.medidesk.mobile.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pl.medidesk.mobile.core.mappers.toDomain
import pl.medidesk.mobile.core.mappers.toEntity
import pl.medidesk.mobile.core.model.Participant
import pl.medidesk.mobile.core.network.dto.MenteeDto
import pl.medidesk.mobile.core.network.dto.ParticipantDto

class BackstageDtoRetirementTest {

    @Test
    fun test_backstage_identifier_is_absent_from_transport_and_domain_models() {
        // Arrange
        val models = listOf(
            ParticipantDto::class.java,
            MenteeDto::class.java,
            Participant::class.java
        )

        // Act
        val exposesBackstageIdentifier = models.any { model ->
            model.declaredFields.any { it.name == BACKSTAGE_FIELD }
        }

        // Assert
        assertFalse(exposesBackstageIdentifier)
    }

    @Test
    fun test_current_ticket_identifiers_survive_mapping_to_domain() {
        // Arrange
        val dto = ParticipantDto(
            id = PARTICIPANT_ID,
            ticketId = TICKET_ID,
            ticketNumber = TICKET_NUMBER,
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

        // Act
        val entity = dto.toEntity(EVENT_ID)
        val domain = entity.toDomain()

        // Assert
        assertEquals(TICKET_ID, domain.ticketId)
        assertEquals(TICKET_NUMBER, entity.ticketNumber)
    }

    private companion object {
        const val BACKSTAGE_FIELD = "backstageTicketId"
        const val EVENT_ID = "event-test-001"
        const val PARTICIPANT_ID = 42L
        const val TICKET_ID = "ticket-current-001"
        const val TICKET_NUMBER = "EVT-000042"
    }
}
