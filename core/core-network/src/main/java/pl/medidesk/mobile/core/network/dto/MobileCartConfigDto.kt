package pl.medidesk.mobile.core.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * WO-154 — Mobile cart configuration response.
 * Backend: GET /api/mobile/events/<id>/cart-config
 *
 * Konwencja: response używa snake_case na wire → @Json(name=...) + camelCase Kotlin.
 */
@JsonClass(generateAdapter = true)
data class MobileCartConfigDto(
    @Json(name = "event_id") val eventId: String?,
    @Json(name = "event_name") val eventName: String?,
    val currency: String? = "PLN",
    @Json(name = "ticket_classes") val ticketClasses: List<MobileCartTicketClassDto> = emptyList(),
    @Json(name = "participant_fields") val participantFields: List<FormFieldDefDto>? = null,
    @Json(name = "payer_fields") val payerFields: List<FormFieldDefDto>? = null,
    @Json(name = "company_fields") val companyFields: List<FormFieldDefDto>? = null,
    val consents: MobileCartConsentsConfigDto? = null,
    @Json(name = "payment_methods") val paymentMethods: List<MobileCartPaymentMethodDto> = emptyList(),
    @Json(name = "sales_closed") val salesClosed: Boolean = false,
    @Json(name = "sales_closed_message") val salesClosedMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class MobileCartTicketClassDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @Json(name = "price_net") val priceNet: Double = 0.0,
    @Json(name = "price_gross") val priceGross: Double = 0.0,
    @Json(name = "vat_rate") val vatRate: Int = 23,
    val currency: String = "PLN",
    @Json(name = "max_quantity") val maxQuantity: Int = 999,
    @Json(name = "min_quantity") val minQuantity: Int = 1,
    val available: Boolean = true
)

@JsonClass(generateAdapter = true)
data class MobileCartPaymentMethodDto(
    val id: String,
    val label: String,
    val available: Boolean = true,
    @Json(name = "deadline_days") val deadlineDays: Int? = null
)

@JsonClass(generateAdapter = true)
data class MobileCartConsentsConfigDto(
    val checkboxes: List<MobileCartConsentCheckboxDto> = emptyList(),
    @Json(name = "info_blocks") val infoBlocks: List<MobileCartConsentInfoBlockDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MobileCartConsentCheckboxDto(
    val id: String,
    val label: String,
    val required: Boolean = false,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class MobileCartConsentInfoBlockDto(
    val id: String? = null,
    val title: String? = null,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class FormFieldDefDto(
    val id: String,
    val label: String,
    val type: String = "text",
    val visible: Boolean = true,
    val required: Boolean = false,
    val placeholder: String? = null,
    val options: List<FormFieldOptionDto>? = null
)

@JsonClass(generateAdapter = true)
data class FormFieldOptionDto(
    val value: String,
    val label: String
)
