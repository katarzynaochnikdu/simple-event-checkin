package pl.medidesk.mobile.feature.addorder.presentation.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.MobileCheckoutCompanyDataDto
import pl.medidesk.mobile.core.network.dto.MobileCheckoutParticipantDto
import pl.medidesk.mobile.core.network.dto.MobileCheckoutPayerDto
import pl.medidesk.mobile.core.network.dto.MobileCheckoutPayloadDto
import pl.medidesk.mobile.core.network.dto.ValidateDiscountRequest
import pl.medidesk.mobile.feature.addorder.domain.FormFieldType
import pl.medidesk.mobile.feature.addorder.domain.toDomain
import pl.medidesk.mobile.feature.addorder.presentation.state.AddOrderResult
import pl.medidesk.mobile.feature.addorder.presentation.state.AddOrderUiState
import pl.medidesk.mobile.feature.addorder.presentation.state.AppliedDiscount
import pl.medidesk.mobile.feature.addorder.presentation.state.PayerFormData
import pl.medidesk.mobile.feature.addorder.presentation.state.TOTAL_STEPS
import javax.inject.Inject

@HiltViewModel
class AddOrderViewModel @Inject constructor(
    private val api: MobileApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddOrderUiState())
    val uiState: StateFlow<AddOrderUiState> = _uiState.asStateFlow()

    fun loadCartConfig(eventId: String) {
        if (_uiState.value.cartConfig != null) return
        _uiState.value = _uiState.value.copy(isLoadingConfig = true, configError = null)
        viewModelScope.launch {
            try {
                val response = api.getCartConfig(eventId)
                if (response.isSuccessful && response.body() != null) {
                    val cfg = response.body()!!.toDomain()
                    val initialPaymentMethod = cfg.paymentMethods.firstOrNull { it.available }?.id
                    _uiState.value = _uiState.value.copy(
                        cartConfig = cfg,
                        isLoadingConfig = false,
                        paymentMethodId = initialPaymentMethod,
                        consentValues = cfg.consents.checkboxes.associate { it.id to false }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingConfig = false,
                        configError = "Nie udało się załadować konfiguracji koszyka (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingConfig = false,
                    configError = "Błąd połączenia: ${e.message ?: "nieznany"}"
                )
            }
        }
    }

    fun setStep(n: Int) {
        if (n in 1..TOTAL_STEPS) {
            _uiState.value = _uiState.value.copy(step = n, errors = emptyMap())
        }
    }

    fun nextStep() {
        if (validateCurrentStep()) {
            val cur = _uiState.value.step
            if (cur < TOTAL_STEPS) setStep(cur + 1)
        }
    }

    fun prevStep() {
        val cur = _uiState.value.step
        if (cur > 1) setStep(cur - 1)
    }

    fun selectTicketClass(id: String) {
        _uiState.value = _uiState.value.copy(selectedTicketClassId = id)
    }

    fun updateParticipantField(id: String, value: String) {
        val cur = _uiState.value
        _uiState.value = cur.copy(
            participantData = cur.participantData + (id to value),
            errors = cur.errors - id
        )
    }

    fun updatePayer(updater: (PayerFormData) -> PayerFormData) {
        _uiState.value = _uiState.value.copy(payer = updater(_uiState.value.payer))
    }

    fun togglePayerCompany(isCompany: Boolean) {
        updatePayer { it.copy(isCompany = isCompany) }
    }

    fun copyParticipantToPayer() {
        val s = _uiState.value
        val firstName = s.participantData["firstName"]?.trim().orEmpty()
        val lastName = s.participantData["lastName"]?.trim().orEmpty()
        val email = s.participantData["email"]?.trim().orEmpty()
        val phone = s.participantData["phone"]?.trim().orEmpty()
        _uiState.value = s.copy(
            payer = s.payer.copy(
                firstName = firstName.ifBlank { s.payer.firstName },
                lastName = lastName.ifBlank { s.payer.lastName },
                email = email.ifBlank { s.payer.email },
                phone = phone.ifBlank { s.payer.phone }
            ),
            errors = s.errors - setOf("firstName", "lastName", "email")
        )
    }

    fun toggleConsent(id: String, value: Boolean) {
        val cur = _uiState.value
        _uiState.value = cur.copy(
            consentValues = cur.consentValues + (id to value),
            errors = cur.errors - "consent_$id"
        )
    }

    fun selectPaymentMethod(id: String) {
        _uiState.value = _uiState.value.copy(paymentMethodId = id)
    }

    fun updateDiscountCode(code: String) {
        // Czyść applied discount przy zmianie kodu (user musi kliknąć "Zastosuj" ponownie)
        _uiState.value = _uiState.value.copy(
            discountCode = code,
            appliedDiscount = null,
            discountError = null
        )
    }

    fun applyDiscountCode(eventId: String) {
        val s = _uiState.value
        val code = s.discountCode.trim().uppercase()
        if (code.isBlank()) {
            _uiState.value = s.copy(appliedDiscount = null, discountError = null)
            return
        }
        val ticketIds = listOfNotNull(s.selectedTicketClassId)
        _uiState.value = s.copy(isValidatingDiscount = true, discountError = null)
        viewModelScope.launch {
            try {
                val resp = api.validateDiscount(eventId, ValidateDiscountRequest(code, ticketIds))
                val body = resp.body()
                if (resp.isSuccessful && body != null) {
                    if (body.valid) {
                        _uiState.value = _uiState.value.copy(
                            isValidatingDiscount = false,
                            appliedDiscount = AppliedDiscount(
                                code = body.code ?: code,
                                type = body.discountType ?: "percent",
                                percent = body.discountPercent ?: 0.0,
                                value = body.discountValue ?: 0.0,
                                message = body.message ?: "Rabat zastosowany",
                                ticketClassIds = body.ticketClassIds ?: emptyList()
                            ),
                            discountError = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isValidatingDiscount = false,
                            appliedDiscount = null,
                            discountError = body.message ?: "Nieprawidłowy kod rabatowy"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isValidatingDiscount = false,
                        appliedDiscount = null,
                        discountError = "Błąd walidacji (${resp.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isValidatingDiscount = false,
                    appliedDiscount = null,
                    discountError = "Błąd połączenia: ${e.message ?: "nieznany"}"
                )
            }
        }
    }

    fun clearDiscount() {
        _uiState.value = _uiState.value.copy(
            discountCode = "",
            appliedDiscount = null,
            discountError = null
        )
    }

    fun computeFinalGross(): Double {
        val s = _uiState.value
        val ticket = s.cartConfig?.ticketClasses?.firstOrNull { it.id == s.selectedTicketClassId }
            ?: return 0.0
        val gross = ticket.priceGross
        val applied = s.appliedDiscount ?: return gross
        // Sprawdź czy kod dotyczy tego biletu
        if (applied.ticketClassIds.isNotEmpty() && ticket.id !in applied.ticketClassIds) {
            return gross
        }
        val discount = when (applied.type) {
            "fixed" -> applied.value
            else -> gross * (applied.percent / 100.0)
        }
        return maxOf(0.0, gross - discount)
    }

    fun lookupGus(nip: String) {
        val normalized = nip.filter { it.isDigit() }
        if (normalized.length != 10) {
            _uiState.value = _uiState.value.copy(gusError = "NIP musi mieć 10 cyfr")
            return
        }
        _uiState.value = _uiState.value.copy(isLookingUpGus = true, gusError = null)
        viewModelScope.launch {
            try {
                val resp = api.gusLookup(normalized)
                val body = resp.body()
                val d = body?.data
                if (resp.isSuccessful && body?.success == true && d != null) {
                    val cur = _uiState.value
                    _uiState.value = cur.copy(
                        isLookingUpGus = false,
                        gusError = null,
                        payer = cur.payer.copy(
                            nip = normalized,
                            companyName = d.name?.takeIf { it.isNotBlank() } ?: cur.payer.companyName,
                            address = d.street?.takeIf { it.isNotBlank() } ?: cur.payer.address,
                            zip = d.zip?.takeIf { it.isNotBlank() } ?: cur.payer.zip,
                            city = d.city?.takeIf { it.isNotBlank() } ?: cur.payer.city
                        ),
                        errors = cur.errors - "companyName"
                    )
                } else {
                    val errMsg = body?.error?.takeIf { it.isNotBlank() }
                        ?: "Nie znaleziono firmy w GUS — wpisz dane ręcznie"
                    _uiState.value = _uiState.value.copy(
                        isLookingUpGus = false,
                        gusError = errMsg
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLookingUpGus = false,
                    gusError = "GUS niedostępny — wpisz dane ręcznie"
                )
            }
        }
    }

    fun validateCurrentStep(): Boolean {
        val s = _uiState.value
        val errors = mutableMapOf<String, String>()
        when (s.step) {
            1 -> if (s.selectedTicketClassId == null) errors["ticket"] = "Wybierz typ biletu"
            2 -> {
                val fields = s.cartConfig?.participantFields?.takeIf { it.isNotEmpty() }
                    ?: defaultParticipantFields()
                fields.filter { it.visible }.forEach { f ->
                    val v = s.participantData[f.id].orEmpty().trim()
                    if (f.required && v.isBlank()) errors[f.id] = "${f.label} wymagane"
                    else if (f.type == FormFieldType.EMAIL && v.isNotBlank() &&
                        !Patterns.EMAIL_ADDRESS.matcher(v).matches()
                    ) errors[f.id] = "Nieprawidłowy email"
                }
            }
            3 -> {
                if (s.payer.firstName.isBlank()) errors["firstName"] = "Imię wymagane"
                if (s.payer.lastName.isBlank()) errors["lastName"] = "Nazwisko wymagane"
                if (s.payer.email.isBlank()) errors["email"] = "Email wymagany"
                else if (!Patterns.EMAIL_ADDRESS.matcher(s.payer.email).matches())
                    errors["email"] = "Nieprawidłowy email"
                if (s.payer.isCompany) {
                    val nipDigits = s.payer.nip.filter { it.isDigit() }
                    if (nipDigits.length != 10) errors["nip"] = "NIP musi mieć 10 cyfr"
                    if (s.payer.companyName.isBlank()) errors["companyName"] = "Nazwa firmy wymagana"
                    if (s.payer.invoiceComment.length > 500)
                        errors["invoiceComment"] = "Maks 500 znaków"
                }
            }
            4 -> {
                s.cartConfig?.consents?.checkboxes
                    ?.filter { it.required }
                    ?.forEach { c ->
                        if (s.consentValues[c.id] != true)
                            errors["consent_${c.id}"] = "Zgoda wymagana"
                    }
            }
            5 -> { /* Krok kodu rabatowego — opcjonalny, brak walidacji */ }
            6 -> if (s.paymentMethodId == null) errors["payment"] = "Wybierz sposób płatności"
        }
        _uiState.value = s.copy(errors = errors)
        return errors.isEmpty()
    }

    fun submit(eventId: String) {
        if (!validateCurrentStep()) return
        val s = _uiState.value
        val ticketClass = s.cartConfig?.ticketClasses?.firstOrNull { it.id == s.selectedTicketClassId }
            ?: run {
                _uiState.value = s.copy(submitResult = AddOrderResult.Error("Nie wybrano typu biletu"))
                return
            }

        val payerDto = MobileCheckoutPayerDto(
            firstName = s.payer.firstName.trim(),
            lastName = s.payer.lastName.trim(),
            email = s.payer.email.trim(),
            phone = s.payer.phone.trim().ifBlank { null },
            isCompany = s.payer.isCompany,
            companyData = if (s.payer.isCompany) MobileCheckoutCompanyDataDto(
                companyName = s.payer.companyName.trim().ifBlank { null },
                nip = s.payer.nip.filter { it.isDigit() }.ifBlank { null },
                address = s.payer.address.trim().ifBlank { null },
                zip = s.payer.zip.trim().ifBlank { null },
                city = s.payer.city.trim().ifBlank { null },
                invoiceComment = s.payer.invoiceComment.trim().ifBlank { null }
            ) else null
        )

        val customFields = s.participantData.toMap()
        val pFirst = customFields["firstName"]?.trim().orEmpty().ifBlank { s.payer.firstName.trim() }
        val pLast = customFields["lastName"]?.trim().orEmpty().ifBlank { s.payer.lastName.trim() }
        val pEmail = customFields["email"]?.trim().orEmpty().ifBlank { s.payer.email.trim() }
        val pPhone = customFields["phone"]?.trim()?.ifBlank { null }

        val participantDto = MobileCheckoutParticipantDto(
            firstName = pFirst,
            lastName = pLast,
            email = pEmail,
            phone = pPhone,
            ticketTypeId = ticketClass.id,
            ticketTypeName = ticketClass.name,
            customFields = customFields.filterKeys { it !in setOf("firstName", "lastName", "email", "phone") }
                .takeIf { it.isNotEmpty() }
        )

        val consentsMap = s.consentValues.mapValues { (_, v) -> v as Any }
        val discountTrim = s.discountCode.trim()
        val summary: Map<String, Any?>? = if (discountTrim.isNotEmpty())
            mapOf("discountCode" to discountTrim) else null

        val payload = MobileCheckoutPayloadDto(
            payer = payerDto,
            participants = listOf(participantDto),
            summary = summary,
            consents = consentsMap.takeIf { it.isNotEmpty() }
        )

        _uiState.value = s.copy(isSubmitting = true)
        viewModelScope.launch {
            try {
                when (s.paymentMethodId) {
                    "proforma" -> {
                        val r = api.checkoutProforma(eventId, payload)
                        handleResponse(r.isSuccessful, r.code(), r.body()?.eventOrderId,
                            r.errorBody()?.string(),
                            "Proforma wysłana do $pEmail")
                    }
                    "stripe" -> {
                        val r = api.checkoutStripe(eventId, payload)
                        handleResponse(r.isSuccessful, r.code(), r.body()?.eventOrderId,
                            r.errorBody()?.string(),
                            "Email z linkiem wysłany do $pEmail")
                    }
                    "free" -> {
                        val r = api.checkoutFree(eventId, payload)
                        handleResponse(r.isSuccessful, r.code(), r.body()?.eventOrderId,
                            r.errorBody()?.string(),
                            "Uczestnik dodany")
                    }
                    else -> {
                        _uiState.value = s.copy(isSubmitting = false,
                            submitResult = AddOrderResult.Error("Nie wybrano sposobu płatności"))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    submitResult = AddOrderResult.Error("Błąd połączenia: ${e.message ?: "nieznany"}")
                )
            }
        }
    }

    private fun handleResponse(
        successful: Boolean,
        code: Int,
        orderId: String?,
        errorBody: String?,
        successMsg: String
    ) {
        if (successful && orderId != null) {
            _uiState.value = _uiState.value.copy(
                isSubmitting = false,
                submitResult = AddOrderResult.Success(orderId, successMsg)
            )
        } else {
            val parsedErr = parseErrorMessage(errorBody)
            val errMsg = when {
                parsedErr != null -> parsedErr
                code == 401 -> "Sesja wygasła — zaloguj się ponownie"
                code == 403 -> "Brak uprawnień do tej operacji"
                else -> "Błąd serwera ($code)"
            }
            _uiState.value = _uiState.value.copy(
                isSubmitting = false,
                submitResult = AddOrderResult.Error(errMsg)
            )
        }
    }

    /**
     * Parsuje komunikat błędu z JSON body backendu.
     * Backend zwraca {"error": "..."} lub {"error": "...", "message": "..."}.
     */
    private fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            // "message" preferowany (user-friendly PL), fallback na "error"
            val msgRegex = """"message"\s*:\s*"([^"]+)"""".toRegex()
            msgRegex.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                ?: """"error"\s*:\s*"([^"]+)"""".toRegex()
                    .find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun consumeSubmitResult() {
        _uiState.value = _uiState.value.copy(submitResult = null)
    }

    private fun defaultParticipantFields() = listOf(
        pl.medidesk.mobile.feature.addorder.domain.FormFieldDefinition(
            id = "firstName", label = "Imię", type = FormFieldType.TEXT,
            visible = true, required = true, placeholder = null, options = emptyList()
        ),
        pl.medidesk.mobile.feature.addorder.domain.FormFieldDefinition(
            id = "lastName", label = "Nazwisko", type = FormFieldType.TEXT,
            visible = true, required = true, placeholder = null, options = emptyList()
        ),
        pl.medidesk.mobile.feature.addorder.domain.FormFieldDefinition(
            id = "email", label = "Email", type = FormFieldType.EMAIL,
            visible = true, required = true, placeholder = null, options = emptyList()
        ),
        pl.medidesk.mobile.feature.addorder.domain.FormFieldDefinition(
            id = "phone", label = "Telefon", type = FormFieldType.TEL,
            visible = true, required = false, placeholder = null, options = emptyList()
        )
    )
}
