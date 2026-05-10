package pl.medidesk.mobile.core.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * WO-154 — Mobile checkout request payload (proforma / stripe / free).
 *
 * KRYTYCZNA UWAGA: Kontrakt request mixuje case'y:
 * - Pola top-level (`payer`, `participants`, `summary`, `consents`) — camelCase w wire
 * - Pola wewnątrz `payer` (firstName, lastName, isCompany, companyData...) — camelCase w wire
 * - Pola wewnątrz `companyData` (companyName, nip, invoiceComment...) — camelCase w wire
 * - Pola wewnątrz `participants[]` — głównie camelCase, ALE `badge_name` jest snake.
 *
 * Stąd: większość property bez `@Json(name=...)`, tylko `badgeName → @Json("badge_name")`.
 */
@JsonClass(generateAdapter = true)
data class MobileCheckoutPayloadDto(
    val payer: MobileCheckoutPayerDto,
    val participants: List<MobileCheckoutParticipantDto>,
    val summary: Map<String, Any?>? = null,
    val consents: Map<String, Any?>? = null,
    val ghost: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MobileCheckoutPayerDto(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null,
    val isCompany: Boolean = false,
    val companyData: MobileCheckoutCompanyDataDto? = null
)

@JsonClass(generateAdapter = true)
data class MobileCheckoutCompanyDataDto(
    val companyName: String? = null,
    val nip: String? = null,
    val address: String? = null,
    val zip: String? = null,
    val city: String? = null,
    val invoiceComment: String? = null
)

@JsonClass(generateAdapter = true)
data class MobileCheckoutParticipantDto(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null,
    val ticketTypeId: String,
    val ticketTypeName: String? = null,
    val company: String? = null,
    @Json(name = "badge_name") val badgeName: String? = null,
    val customFields: Map<String, String>? = null
)
