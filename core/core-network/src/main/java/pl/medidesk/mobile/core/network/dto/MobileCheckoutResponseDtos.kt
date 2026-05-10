package pl.medidesk.mobile.core.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * WO-154 — Mobile checkout response DTOs (3 warianty: proforma, stripe, free).
 * Wszystkie response używają snake_case na wire → @Json(name=...) + camelCase Kotlin.
 */
@JsonClass(generateAdapter = true)
data class MobileCheckoutProformaResponseDto(
    @Json(name = "event_order_id") val eventOrderId: String,
    @Json(name = "proforma_number") val proformaNumber: String? = null,
    @Json(name = "proforma_error") val proformaError: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MobileCheckoutStripeResponseDto(
    val url: String,
    @Json(name = "session_id") val sessionId: String? = null,
    @Json(name = "event_order_id") val eventOrderId: String
)

@JsonClass(generateAdapter = true)
data class MobileCheckoutFreeResponseDto(
    @Json(name = "event_order_id") val eventOrderId: String,
    val message: String? = null
)
