package pl.medidesk.mobile.core.model

data class WalkinParticipant(
    val id: Long,
    val walkInCode: String,
    val eventId: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val company: String?,
    val ticketClassId: String?,
    val ticketName: String?,
    val notes: String?,
    val checkedInAt: String?,
    val status: String,
    val createdAt: String,
    val syncStatus: String
) {
    val displayName: String get() = "$firstName $lastName"
}
