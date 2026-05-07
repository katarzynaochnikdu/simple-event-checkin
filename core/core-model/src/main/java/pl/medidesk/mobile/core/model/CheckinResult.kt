package pl.medidesk.mobile.core.model

data class CheckinResult(
    val success: Boolean,
    val alreadyCheckedIn: Boolean = false,
    val checkedInAt: String? = null,
    val participant: ParticipantSummary? = null,
    val error: String? = null,
    val isOffline: Boolean = false
)

data class ParticipantSummary(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val company: String,
    val ticketName: String,
    val ticketClassId: String
) {
    val displayName: String get() = "$firstName $lastName"
}
