package pl.medidesk.mobile.feature.addorder.presentation.state

import pl.medidesk.mobile.feature.addorder.domain.OrderCartConfig

/**
 * State multi-step "Dodaj zamówienie" sheet (WO-155).
 */
data class AddOrderUiState(
    val step: Int = 1,                                          // 1..5
    val cartConfig: OrderCartConfig? = null,
    val isLoadingConfig: Boolean = false,
    val configError: String? = null,
    val selectedTicketClassId: String? = null,
    val participantData: Map<String, String> = emptyMap(),
    val payer: PayerFormData = PayerFormData(),
    val consentValues: Map<String, Boolean> = emptyMap(),
    val paymentMethodId: String? = null,
    val discountCode: String = "",
    val isValidatingDiscount: Boolean = false,
    val appliedDiscount: AppliedDiscount? = null,
    val discountError: String? = null,
    val isLookingUpGus: Boolean = false,
    val gusError: String? = null,
    val isSubmitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val submitResult: AddOrderResult? = null
)

data class PayerFormData(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val isCompany: Boolean = false,
    val nip: String = "",
    val companyName: String = "",
    val address: String = "",
    val zip: String = "",
    val city: String = "",
    val invoiceComment: String = ""
)

sealed class AddOrderResult {
    data class Success(val orderId: String, val message: String) : AddOrderResult()
    data class Error(val message: String) : AddOrderResult()
}

data class AppliedDiscount(
    val code: String,
    val type: String,                  // "percent" | "fixed"
    val percent: Double,               // 0..100 dla percent, 0 dla fixed
    val value: Double,                 // value PLN dla fixed, 0 dla percent
    val message: String,
    val ticketClassIds: List<String>   // empty = wszystkie bilety
)

const val TOTAL_STEPS = 6
