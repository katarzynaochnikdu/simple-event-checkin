package pl.medidesk.mobile.core.model

data class CheckinResult(
    val success: Boolean,
    val alreadyCheckedIn: Boolean = false,
    val checkedInAt: String? = null,
    val participant: ParticipantSummary? = null,
    val error: String? = null,
    val isOffline: Boolean = false,
    val ticketNumber: String? = null
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
     * NIE uprawniają do wejścia i nie biorą udziału w check-inie.
     */
    val pendingTicketNames: List<String> = emptyList()
) {
    val displayName: String get() = "$firstName $lastName"
    val entitlementNames: List<String>
        get() = entitlementDisplayNames(tickets, ticketName)
}
