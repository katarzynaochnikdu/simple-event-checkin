package pl.medidesk.mobile.core.model

data class CheckinResult(
    val success: Boolean,
    val alreadyCheckedIn: Boolean = false,
    val checkedInAt: String? = null,
    val participant: ParticipantSummary? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
    val ticketNumber: String? = null,
    /**
     * Suma dopłat w toku, sformatowana przez backend (np. "300,11").
     * Sam tekst do pokazania — apka niczego nie przelicza.
     */
    val surchargeDue: String? = null
)

data class ParticipantSummary(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val company: String,
    val ticketName: String,
    val ticketClassId: String,
    val tickets: List<TicketEntitlement> = emptyList(),
    val ticketNumber: String = "",
    /**
     * Bilety czekające na opłacenie dopłaty — wyłącznie do pokazania obsłudze.
     * Uprawnienie liczy się od wystawienia proformy — taki bilet DAJE wstęp
     * i świadczenia; oznaczamy go tylko po to, żeby obsługa mogła przypomnieć
     * o płatności przy rejestracji.
     */
    val pendingTicketNames: List<String> = emptyList()
) {
    val displayName: String get() = "$firstName $lastName"
    val entitlementNames: List<String>
        get() = entitlementDisplayNames(tickets, ticketName)
}
