package pl.medidesk.mobile.core.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class CheckinRequest(
    @Json(name = "ticket_id") val ticketId: String,
    @Json(name = "event_id") val eventId: String,
    @Json(name = "scanned_at") val scannedAt: String,
    @Json(name = "device_id") val deviceId: String = "android"
)

@JsonClass(generateAdapter = true)
data class UndoCheckinRequest(
    @Json(name = "ticket_id") val ticketId: String,
    @Json(name = "event_id") val eventId: String,
    @Json(name = "device_id") val deviceId: String = "android"
)

@JsonClass(generateAdapter = true)
data class CheckinSyncRequest(
    val items: List<CheckinSyncItem>
)

@JsonClass(generateAdapter = true)
data class CheckinSyncItem(
    @Json(name = "ticket_id") val ticketId: String,
    @Json(name = "event_id") val eventId: String,
    @Json(name = "scanned_at") val scannedAt: String,
    @Json(name = "device_id") val deviceId: String = "android",
    val action: String = "checkin"
)

@JsonClass(generateAdapter = true)
data class WalkinRequest(
    @Json(name = "event_id") val eventId: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "walk_in_code") val walkInCode: String,
    val email: String? = null,
    val phone: String? = null,
    val company: String? = null,
    @Json(name = "ticket_class_id") val ticketClassId: String? = null,
    val notes: String? = null,
    @Json(name = "checked_in_at") val checkedInAt: String? = null,
    @Json(name = "device_id") val deviceId: String = "android"
)

@JsonClass(generateAdapter = true)
data class WalkinBatchRequest(
    val items: List<WalkinRequest>
)

@JsonClass(generateAdapter = true)
data class InHubConfigRequest(
    val pin: String,
    @Json(name = "auto_checkin") val autoCheckin: Boolean = true,
    @Json(name = "show_search") val showSearch: Boolean = true,
    @Json(name = "show_walkin") val showWalkin: Boolean = false
)

@JsonClass(generateAdapter = true)
data class VerifyPinRequest(
    val pin: String
)

@JsonClass(generateAdapter = true)
data class OrderStatusUpdateRequest(
    val status: String
)

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    val token: String,
    @Json(name = "new_password") val newPassword: String
)
