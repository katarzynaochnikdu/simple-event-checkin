package pl.medidesk.mobile.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketPoolFilterTest {

    @Test
    fun participantMatchesTicketPool_withoutFilter_keepsEveryone() {
        // Act
        val matches = participantMatchesTicketPool(
            primaryTicketClassId = "cls-basic",
            ticketNames = listOf("Basic"),
            selectedTicketClassId = null,
            selectedTicketClassName = null
        )

        assertTrue(matches)
    }

    @Test
    fun participantMatchesTicketPool_primaryTicketInPool_matches() {
        // Act
        val matches = participantMatchesTicketPool(
            primaryTicketClassId = "cls-basic",
            ticketNames = listOf("Basic"),
            selectedTicketClassId = "cls-basic",
            selectedTicketClassName = "Basic"
        )

        assertTrue(matches)
    }

    @Test
    fun participantMatchesTicketPool_secondaryTicketInPool_matches() {
        // Arrange: osoba z biletem głównym "Basic" dokupiła "Basic+ 24.09"
        val ticketNames = listOf("Basic", "Basic+ 24.09")

        // Act
        val matches = participantMatchesTicketPool(
            primaryTicketClassId = "cls-basic",
            ticketNames = ticketNames,
            selectedTicketClassId = "cls-basic-plus-2409",
            selectedTicketClassName = "Basic+ 24.09"
        )

        assertTrue(matches)
    }

    @Test
    fun participantMatchesTicketPool_ignoresCaseAndSurroundingSpaces() {
        // Act
        val matches = participantMatchesTicketPool(
            primaryTicketClassId = "cls-basic",
            ticketNames = listOf("Basic", "  basic+ 24.09 "),
            selectedTicketClassId = "cls-basic-plus-2409",
            selectedTicketClassName = "Basic+ 24.09"
        )

        assertTrue(matches)
    }

    @Test
    fun participantMatchesTicketPool_noTicketInPool_doesNotMatch() {
        // Act
        val matches = participantMatchesTicketPool(
            primaryTicketClassId = "cls-basic",
            ticketNames = listOf("Basic", "Warsztat X"),
            selectedTicketClassId = "cls-basic-plus-2409",
            selectedTicketClassName = "Basic+ 24.09"
        )

        assertFalse(matches)
    }

    @Test
    fun participantMatchesTicketPool_unknownPoolName_fallsBackToPrimaryTicketOnly() {
        // Arrange: lista pul jeszcze nie dojechała — znamy tylko identyfikator filtra
        // Act
        val matchesOther = participantMatchesTicketPool(
            primaryTicketClassId = "cls-basic",
            ticketNames = listOf("Basic", "Basic+ 24.09"),
            selectedTicketClassId = "cls-basic-plus-2409",
            selectedTicketClassName = null
        )
        val matchesPrimary = participantMatchesTicketPool(
            primaryTicketClassId = "cls-basic",
            ticketNames = listOf("Basic", "Basic+ 24.09"),
            selectedTicketClassId = "cls-basic",
            selectedTicketClassName = null
        )

        assertFalse(matchesOther)
        assertTrue(matchesPrimary)
    }
}
