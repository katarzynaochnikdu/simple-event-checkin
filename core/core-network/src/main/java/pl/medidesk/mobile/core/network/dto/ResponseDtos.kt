package pl.medidesk.mobile.core.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    @Json(name = "must_change_password") val mustChangePassword: Boolean = false,
    val user: UserDto? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: Int,
    val email: String,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    val role: String?
)

@JsonClass(generateAdapter = true)
data class EventsResponse(
    val events: List<EventDto>
)

@JsonClass(generateAdapter = true)
data class EventDto(
    @Json(name = "event_id") val eventId: String?,
    val id: String?,
    @Json(name = "event_name") val eventName: String?,
    val name: String?,
    val title: String?,
    val status: String?,
    @Json(name = "start_date") val startDate: String?,
    @Json(name = "starts_at") val startsAt: String?,
    @Json(name = "start_at") val startAt: String?,
    val date: String?,
    val start: String?,
    val startTime: String?,
    @Json(name = "event_date") val eventDate: String?,
    @Json(name = "end_date") val endDate: String?,
    val venue: String?,
    val location: String?,
    val address: String?,
    @Json(name = "image_url") val imageUrl: String?,
    val image: String?,
    val thumbnail: String?,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "logo_color_url") val logoColorUrl: String? = null,
    @Json(name = "logo_white_url") val logoWhiteUrl: String? = null,
    @Json(name = "primary_color") val primaryColor: String? = null,
    @Json(name = "secondary_color") val secondaryColor: String? = null,
    @Json(name = "accent_color") val accentColor: String? = null
)

@JsonClass(generateAdapter = true)
data class ParticipantsResponse(
    @Json(name = "event_id") val eventId: String,
    val count: Int,
    val participants: List<ParticipantDto>
)

@JsonClass(generateAdapter = true)
data class ParticipantDto(
    val id: Long,
    @Json(name = "ticket_id") val ticketId: String?,
    @Json(name = "ticket_number") val ticketNumber: String? = null,
    @Json(name = "backstage_ticket_id") val backstageTicketId: String?,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    val email: String?,
    val phone: String? = null,
    val company: String?,
    @Json(name = "ticket_class_id") val ticketClassId: String?,
    @Json(name = "ticket_name") val ticketName: String?,
    val status: String?,
    @Json(name = "attendance_status") val attendanceStatus: String?,
    @Json(name = "event_order_id") val eventOrderId: String?,
    @Json(name = "checked_in_at") val checkedInAt: String?,
    @Json(name = "order_status") val orderStatus: String? = null,
    @Json(name = "is_walkin") val isWalkin: Boolean = false,
    val tags: List<String>? = null,
    @Json(name = "buyer_name") val buyerName: String? = null,
    @Json(name = "buyer_email") val buyerEmail: String? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "purchaser_nip") val purchaserNip: String? = null,
    @Json(name = "purchaser_company") val purchaserCompany: String? = null,
    @Json(name = "order_participants_total") val orderParticipantsTotal: Int? = null,
    @Json(name = "order_participants_checked_in") val orderParticipantsCheckedIn: Int? = null,
    @Json(name = "rsvp_sent") val rsvpSent: Boolean = false,
    @Json(name = "rsvp_response") val rsvpResponse: String? = null,
    @Json(name = "rsvp_responded_at") val rsvpRespondedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CheckinResponse(
    val success: Boolean,
    @Json(name = "already_checked_in") val alreadyCheckedIn: Boolean = false,
    @Json(name = "checked_in_at") val checkedInAt: String? = null,
    val participant: ParticipantSummaryDto? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ParticipantSummaryDto(
    val id: Long,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    val email: String? = null,
    val company: String? = null,
    @Json(name = "ticket_name") val ticketName: String? = null,
    @Json(name = "ticket_class_id") val ticketClassId: String? = null
)

@JsonClass(generateAdapter = true)
data class CheckinStatsResponse(
    @Json(name = "event_id") val eventId: String,
    @Json(name = "total_with_qr") val totalWithQr: Int,
    @Json(name = "checked_in") val checkedIn: Int,
    val scanners: List<ScannerStatDto>
)

@JsonClass(generateAdapter = true)
data class ScannerStatDto(
    @Json(name = "scanned_by") val scannedBy: String,
    @Json(name = "scan_count") val scanCount: Int
)

@JsonClass(generateAdapter = true)
data class DashboardResponse(
    @Json(name = "event_id") val eventId: String?,
    @Json(name = "total_registered") val totalRegistered: Int?,
    @Json(name = "total_with_qr") val totalWithQr: Int?,
    @Json(name = "checked_in") val checkedIn: Int?,
    @Json(name = "walk_ins") val walkIns: Int?,
    @Json(name = "check_in_rate") val checkInRate: Double?,
    @Json(name = "by_ticket_class") val byTicketClass: List<TicketClassStatDto>?,
    val timeline: List<TimelineEntryDto>?,
    @Json(name = "top_scanners") val topScanners: List<TopScannerDto>?,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "primary_color") val primaryColor: String? = null,
    @Json(name = "secondary_color") val secondaryColor: String? = null,
    @Json(name = "accent_color") val accentColor: String? = null
)

@JsonClass(generateAdapter = true)
data class TicketClassStatDto(
    @Json(name = "ticket_name") val ticketName: String,
    val total: Int,
    @Json(name = "checked_in") val checkedIn: Int
)

@JsonClass(generateAdapter = true)
data class TimelineEntryDto(
    val hour: String,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class TopScannerDto(
    val email: String,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class TicketClassesResponse(val ticket_classes: List<TicketClassDto>)
@JsonClass(generateAdapter = true)
data class TicketClassDto(val ticket_class_id: String, val ticket_name: String, val event_id: String)
@JsonClass(generateAdapter = true)
data class CheckinSyncResponse(val success: Boolean)
@JsonClass(generateAdapter = true)
data class WalkinResponse(val success: Boolean)
@JsonClass(generateAdapter = true)
data class WalkinBatchResponse(val success: Boolean)
@JsonClass(generateAdapter = true)
data class WalkinsListResponse(val walkins: List<String>)

@JsonClass(generateAdapter = true)
data class InHubConfigResponse(
    val exists: Boolean,
    val id: Int? = null,
    @Json(name = "event_id") val eventId: String? = null,
    @Json(name = "auto_checkin") val autoCheckin: Boolean? = null,
    @Json(name = "show_search") val showSearch: Boolean? = null,
    @Json(name = "show_walkin") val showWalkin: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class VerifyPinResponse(val valid: Boolean)
@JsonClass(generateAdapter = true)
data class GusLookupResponse(
    val success: Boolean,
    val data: GusCompanyDataDto? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ValidateDiscountRequest(
    val code: String,
    val ticketTypeIds: List<String>
)

@JsonClass(generateAdapter = true)
data class ValidateDiscountResponse(
    val valid: Boolean,
    val code: String? = null,
    @Json(name = "discount_percent") val discountPercent: Double? = null,
    @Json(name = "discount_type") val discountType: String? = null,
    @Json(name = "discount_value") val discountValue: Double? = null,
    @Json(name = "ticket_class_ids") val ticketClassIds: List<String>? = null,
    @Json(name = "remaining_uses") val remainingUses: Int? = null,
    val message: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class GusCompanyDataDto(
    val name: String? = null,
    val regon: String? = null,
    val street: String? = null,
    val zip: String? = null,
    val city: String? = null,
    val voivodeship: String? = null,
    val krs: String? = null
)

// --- Speakers ---

@JsonClass(generateAdapter = true)
data class SpeakersResponse(
    val speakers: List<SpeakerDto>,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class SpeakerDto(
    @Json(name = "speaker_id") val speakerId: String,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    val title: String? = null,
    val affiliation: String? = null,
    val organization: String? = null,
    @Json(name = "photo_url") val photoUrl: String? = null,
    val bio: String? = null,
    @Json(name = "bio_long") val bioLong: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @Json(name = "social_linkedin") val socialLinkedin: String? = null,
    @Json(name = "social_twitter") val socialTwitter: String? = null,
    val website: String? = null,
    @Json(name = "academic_title") val academicTitle: String? = null
)

// --- Sponsors ---

@JsonClass(generateAdapter = true)
data class EventSponsorsResponse(
    val sponsors: List<EventSponsorDto>,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class EventSponsorDto(
    @Json(name = "event_sponsor_id") val eventSponsorId: Long,
    @Json(name = "sponsor_company_id") val sponsorCompanyId: Long,
    @Json(name = "company_name") val companyName: String,
    @Json(name = "company_name_short") val companyNameShort: String,
    @Json(name = "company_logo_url") val companyLogoUrl: String? = null,
    @Json(name = "industry_category") val industryCategory: String? = null,
    @Json(name = "package_label") val packageLabel: String? = null,
    @Json(name = "package_color") val packageColor: String? = null,
    @Json(name = "pipeline_status") val pipelineStatus: String? = null,
    @Json(name = "deal_type") val dealType: String? = null,
    @Json(name = "contract_value_net") val contractValueNet: Double? = null,
    val tags: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class SponsorDetailResponse(
    @Json(name = "event_sponsor_id") val eventSponsorId: Long,
    val company: SponsorCompanyDto,
    @Json(name = "package_label") val packageLabel: String? = null,
    @Json(name = "package_color") val packageColor: String? = null,
    @Json(name = "pipeline_status") val pipelineStatus: String? = null,
    @Json(name = "deal_type") val dealType: String? = null,
    @Json(name = "contract_value_net") val contractValueNet: Double? = null,
    @Json(name = "ops_status") val opsStatus: String? = null,
    val tags: List<String>? = null,
    val contacts: List<ContactPersonDto>? = null,
    val benefits: List<SponsorBenefitDto>? = null
)

@JsonClass(generateAdapter = true)
data class SponsorCompanyDto(
    val id: Long,
    val name: String,
    @Json(name = "name_short") val nameShort: String,
    val nip: String? = null,
    @Json(name = "industry_category") val industryCategory: String? = null,
    val website: String? = null,
    @Json(name = "email_general") val emailGeneral: String? = null,
    @Json(name = "phone_general") val phoneGeneral: String? = null,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "address_city") val addressCity: String? = null,
    @Json(name = "address_street") val addressStreet: String? = null,
    @Json(name = "address_postal_code") val addressPostalCode: String? = null,
    @Json(name = "cooperation_status") val cooperationStatus: String? = null
)

@JsonClass(generateAdapter = true)
data class ContactPersonDto(
    val id: Long,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val position: String? = null,
    val department: String? = null
)

@JsonClass(generateAdapter = true)
data class SponsorBenefitDto(
    val name: String,
    val status: String? = null,
    val category: String? = null
)

// Companies
@JsonClass(generateAdapter = true)
data class CompaniesResponse(
    @Json(name = "event_id") val eventId: String,
    @Json(name = "total_companies") val totalCompanies: Int,
    val companies: List<CompanyDto>
)

@JsonClass(generateAdapter = true)
data class CompanyDto(
    @Json(name = "company_name") val companyName: String,
    val role: String,
    @Json(name = "participant_count") val participantCount: Int,
    @Json(name = "checked_in_count") val checkedInCount: Int,
    val persons: List<CompanyPersonDto>? = null,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "sponsor_company_id") val sponsorCompanyId: Long? = null,
    @Json(name = "company_name_short") val companyNameShort: String? = null,
    val industry: String? = null,
    val website: String? = null,
    @Json(name = "pipeline_status") val pipelineStatus: String? = null,
    @Json(name = "ops_status") val opsStatus: String? = null,
    @Json(name = "deal_type") val dealType: String? = null,
    @Json(name = "contract_value") val contractValue: Double? = null
)

@JsonClass(generateAdapter = true)
data class CompanyPersonDto(
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    val email: String? = null,
    val position: String? = null,
    val phone: String? = null,
    @Json(name = "ticket_class") val ticketClass: String? = null,
    @Json(name = "checked_in") val checkedIn: Boolean? = null,
    @Json(name = "order_status") val orderStatus: String? = null
)

// Orders
@JsonClass(generateAdapter = true)
data class OrdersResponse(
    @Json(name = "event_id") val eventId: String,
    @Json(name = "total_orders") val totalOrders: Int,
    @Json(name = "paid_count") val paidCount: Int,
    @Json(name = "total_revenue") val totalRevenue: Double,
    val currency: String,
    val orders: List<OrderDto>
)

@JsonClass(generateAdapter = true)
data class OrderDto(
    @Json(name = "event_order_id") val eventOrderId: String,
    @Json(name = "purchaser_email") val purchaserEmail: String? = null,
    @Json(name = "purchaser_name") val purchaserName: String? = null,
    @Json(name = "purchaser_company") val purchaserCompany: String? = null,
    @Json(name = "purchaser_nip") val purchaserNip: String? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "promo_code") val promoCode: String? = null,
    val total: Double = 0.0,
    val currency: String = "PLN",
    val status: String = "received",
    val sandbox: Boolean = false,
    @Json(name = "participant_count") val participantCount: Int = 0,
    @Json(name = "checked_in_count") val checkedInCount: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

// ─── Image Upload ───────────────────────────────────────────────────
@JsonClass(generateAdapter = true)
data class ImageUploadResponse(
    val success: Boolean = false,
    val url: String? = null,
    val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class GenericActionResponse(
    val success: Boolean,
    val error: String? = null,
    val status: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    @Json(name = "current_password") val currentPassword: String,
    @Json(name = "new_password") val newPassword: String
)

@JsonClass(generateAdapter = true)
data class ChangePasswordResponse(
    val success: Boolean,
    val error: String? = null
)

// --- My Mentees ---

@JsonClass(generateAdapter = true)
data class MenteeDto(
    @Json(name = "participant_id") val participantId: Long,
    @Json(name = "ticket_id") val ticketId: String? = null,
    @Json(name = "ticket_number") val ticketNumber: String? = null,
    @Json(name = "backstage_ticket_id") val backstageTicketId: String? = null,
    @Json(name = "crm_person_id") val crmPersonId: Long? = null,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    val email: String?,
    val phone: String? = null,
    @Json(name = "company_name") val companyName: String?,
    @Json(name = "company_short_name") val companyShortName: String? = null,
    @Json(name = "is_partner") val isPartner: Boolean = false,
    @Json(name = "crm_account_id") val crmAccountId: Long? = null,
    @Json(name = "crm_review360_status") val review360Status: String? = null,
    @Json(name = "ticket_name") val ticketName: String? = null,
    @Json(name = "order_status") val orderStatus: String? = null,
    @Json(name = "payment_type") val paymentType: Int? = null,
    @Json(name = "order_total") val orderTotal: Double? = null,
    @Json(name = "attendance_status") val attendanceStatus: String? = null,
    @Json(name = "checked_in") val checkedIn: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MenteesResponse(
    val success: Boolean,
    val data: List<MenteeDto>
)

@JsonClass(generateAdapter = true)
data class Review360ViewResponse(
    val success: Boolean,
    val url: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteCompanyAssignmentRequest(
    @Json(name = "company_name") val companyName: String
)

@JsonClass(generateAdapter = true)
data class SuccessResponse(
    val success: Boolean,
    val error: String? = null
)
