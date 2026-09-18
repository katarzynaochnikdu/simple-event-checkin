package pl.medidesk.mobile.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketEntitlementTest {

    @Test
    fun entitlementDisplayNames_prefersTicketsList_overFallback() {
        val tickets = listOf(
            TicketEntitlement("Basic+", isPrimary = true),
            TicketEntitlement("Warsztat X")
        )

        val names = entitlementDisplayNames(tickets, "Standard")

        assertEquals(listOf("Basic+", "Warsztat X"), names)
    }

    @Test
    fun entitlementDisplayNames_fallsBackToSingleTicketName_whenListEmpty() {
        val names = entitlementDisplayNames(emptyList(), "Standard")

        assertEquals(listOf("Standard"), names)
    }

    @Test
    fun encodeThenDecode_roundTripsNamesAndFlags() {
        val tickets = listOf(
            TicketEntitlement("Basic+", isPrimary = true, checkedIn = true),
            TicketEntitlement("Cytat \"specjalny\"")
        )

        val decoded = decodeTicketEntitlements(encodeTicketEntitlements(tickets))

        assertEquals(tickets, decoded)
    }

    @Test
    fun decode_emptyOrGarbage_returnsEmpty() {
        assertTrue(decodeTicketEntitlements(null).isEmpty())
        assertTrue(decodeTicketEntitlements("").isEmpty())
        assertTrue(decodeTicketEntitlements("not-json").isEmpty())
    }
}
