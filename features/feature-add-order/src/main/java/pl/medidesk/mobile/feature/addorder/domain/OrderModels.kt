package pl.medidesk.mobile.feature.addorder.domain

import pl.medidesk.mobile.core.network.dto.FormFieldDefDto
import pl.medidesk.mobile.core.network.dto.FormFieldOptionDto
import pl.medidesk.mobile.core.network.dto.MobileCartConfigDto
import pl.medidesk.mobile.core.network.dto.MobileCartConsentCheckboxDto
import pl.medidesk.mobile.core.network.dto.MobileCartConsentInfoBlockDto
import pl.medidesk.mobile.core.network.dto.MobileCartPaymentMethodDto
import pl.medidesk.mobile.core.network.dto.MobileCartTicketClassDto

/**
 * Domain model dla cart-config (mobile add-order). Lokalny w feature-add-order
 * — promote do core-model dopiero gdy drugi feature ich potrzebuje.
 */
data class OrderCartConfig(
    val eventId: String,
    val eventName: String,
    val currency: String,
    val ticketClasses: List<OrderTicketClass>,
    val participantFields: List<FormFieldDefinition>,
    val payerFields: List<FormFieldDefinition>,
    val companyFields: List<FormFieldDefinition>,
    val consents: OrderConsentsConfig,
    val paymentMethods: List<OrderPaymentMethod>,
    val salesClosed: Boolean,
    val salesClosedMessage: String?
)

data class OrderTicketClass(
    val id: String,
    val name: String,
    val description: String?,
    val priceNet: Double,
    val priceGross: Double,
    val vatRate: Int,
    val currency: String,
    val available: Boolean,
    val minQuantity: Int = 1,
    val maxQuantity: Int = 999,
    // WO-296 / WO-287: pricing + availability metadata (backend authoritative).
    val baseGross: Double? = null,
    val finalGross: Double? = null,
    val ebActive: Boolean = false,
    val ebPct: Double = 0.0,
    val ebUntil: String? = null,
    val salesStartDate: String? = null,
    val salesEndDate: String? = null,
    val soldOut: Boolean = false
)

data class OrderPaymentMethod(
    val id: String,            // "proforma" | "stripe" | "free"
    val label: String,
    val available: Boolean,
    val deadlineDays: Int?
)

data class OrderConsentsConfig(
    val checkboxes: List<OrderConsentCheckbox>,
    val infoBlocks: List<OrderConsentInfoBlock>
)

data class OrderConsentCheckbox(
    val id: String,
    val label: String,
    val required: Boolean,
    val url: String?
)

data class OrderConsentInfoBlock(
    val id: String?,
    val title: String?,
    val content: String?
)

enum class FormFieldType { TEXT, EMAIL, TEL, SELECT, TEXTAREA;
    companion object {
        fun parse(s: String?): FormFieldType = when (s?.lowercase()) {
            "email" -> EMAIL
            "tel", "phone" -> TEL
            "select" -> SELECT
            "textarea" -> TEXTAREA
            else -> TEXT
        }
    }
}

data class FormFieldDefinition(
    val id: String,
    val label: String,
    val type: FormFieldType,
    val visible: Boolean,
    val required: Boolean,
    val placeholder: String?,
    val options: List<FormFieldOption>
)

data class FormFieldOption(val value: String, val label: String)

// --- Domain extensions: DTO → domain mapping ---

fun MobileCartConfigDto.toDomain(): OrderCartConfig = OrderCartConfig(
    eventId = eventId.orEmpty(),
    eventName = eventName.orEmpty(),
    currency = currency ?: "PLN",
    ticketClasses = ticketClasses.map { it.toDomain() },
    participantFields = participantFields.orEmpty().map { it.toDomain() },
    payerFields = payerFields.orEmpty().map { it.toDomain() },
    companyFields = companyFields.orEmpty().map { it.toDomain() },
    consents = consents?.let {
        OrderConsentsConfig(
            checkboxes = it.checkboxes.map { c -> c.toDomain() },
            infoBlocks = it.infoBlocks.map { ib -> ib.toDomain() }
        )
    } ?: OrderConsentsConfig(emptyList(), emptyList()),
    paymentMethods = paymentMethods.map { it.toDomain() },
    salesClosed = salesClosed,
    salesClosedMessage = salesClosedMessage
)

private fun MobileCartTicketClassDto.toDomain() = OrderTicketClass(
    id = id, name = name, description = description,
    priceNet = priceNet, priceGross = priceGross, vatRate = vatRate,
    currency = currency, available = available,
    minQuantity = minQuantity.coerceAtLeast(1),
    maxQuantity = maxQuantity.coerceAtLeast(1),
    baseGross = baseGross,
    finalGross = finalGross,
    ebActive = ebActive,
    ebPct = ebPct,
    ebUntil = ebUntil,
    salesStartDate = salesStartDate,
    salesEndDate = salesEndDate,
    soldOut = soldOut
)

private fun MobileCartPaymentMethodDto.toDomain() = OrderPaymentMethod(
    id = id, label = label, available = available, deadlineDays = deadlineDays
)

private fun MobileCartConsentCheckboxDto.toDomain() = OrderConsentCheckbox(
    id = id, label = label, required = required, url = url
)

private fun MobileCartConsentInfoBlockDto.toDomain() = OrderConsentInfoBlock(
    id = id, title = title, content = content
)

private fun FormFieldDefDto.toDomain() = FormFieldDefinition(
    id = id, label = label, type = FormFieldType.parse(type),
    visible = visible, required = required, placeholder = placeholder,
    options = options.orEmpty().map { FormFieldOption(it.value, it.label) }
)
