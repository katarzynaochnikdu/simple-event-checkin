package pl.medidesk.mobile.feature.addorder.presentation.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.analytics.Analytics
import pl.medidesk.mobile.core.analytics.AnalyticsEvent
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
        Analytics.capture(AnalyticsEvent.ADD_ORDER_STARTED, mapOf(AnalyticsEvent.Props.EVENT_ID to eventId))
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
        if (!validateCurrentStep()) return
        val s = _uiState.value
        if (s.step == 2 && s.participantSubStep < s.participantCount - 1) {
            // WO-171: pętla podstron w Kroku 2 — przejście do następnego uczestnika
            val nextSub = s.participantSubStep + 1
            _uiState.value = s.copy(
                participantSubStep = nextSub,
                participantData = s.participantsData.getOrElse(nextSub) { emptyMap() },
                errors = emptyMap()
            )
            return
        }
        if (s.step < TOTAL_STEPS) {
            // Wychodzimy z Kroku 2 do przodu → reset subStep na 0
            val resetSubStep = if (s.step == 2) 0 else s.participantSubStep
            _uiState.value = s.copy(
                step = s.step + 1,
                participantSubStep = resetSubStep,
                errors = emptyMap()
            )
        }
    }

    fun prevStep() {
        val s = _uiState.value
        if (s.step == 2 && s.participantSubStep > 0) {
            // WO-171: cofnięcie do poprzedniego uczestnika w Kroku 2
            val prevSub = s.participantSubStep - 1
            _uiState.value = s.copy(
                participantSubStep = prevSub,
                participantData = s.participantsData.getOrElse(prevSub) { emptyMap() },
                errors = emptyMap()
            )
            return
        }
        if (s.step > 1) {
            _uiState.value = s.copy(step = s.step - 1, errors = emptyMap())
        }
    }

    fun selectTicketClass(id: String) {
        val cur = _uiState.value
        val ticketClass = cur.cartConfig?.ticketClasses?.firstOrNull { it.id == id }
        // WO-176: count startuje OD minQuantity klasy biletu (revert from iter2).
        // User feedback: "masz minimalnie 2 a zaczyna od 1" — start z 1 było mylące.
        // Dla Connect+ (min=2) count = 2 od razu. Dla Connect (min=1) count = 1.
        val newCount = (ticketClass?.minQuantity ?: 1).coerceAtLeast(1)
        val resized = resizeParticipants(cur.participantsData, newCount)
        _uiState.value = cur.copy(
            selectedTicketClassId = id,
            participantCount = newCount,
            participantsData = resized,
            participantSubStep = 0,
            participantData = resized.firstOrNull() ?: emptyMap(),
            errors = cur.errors - "ticket" - "quantity"
        )
    }

    // WO-171: zwiększ licznik uczestników (max = ticketClass.maxQuantity).
    // WO-175: clear quantity error przy zmianie (re-walidacja przy "Dalej").
    fun incrementParticipantCount() {
        val cur = _uiState.value
        val cls = cur.cartConfig?.ticketClasses?.firstOrNull { it.id == cur.selectedTicketClassId } ?: return
        val newCount = (cur.participantCount + 1).coerceAtMost(cls.maxQuantity)
        if (newCount == cur.participantCount) return
        _uiState.value = cur.copy(
            participantCount = newCount,
            participantsData = resizeParticipants(cur.participantsData, newCount),
            errors = cur.errors - "quantity"
        )
    }

    // WO-176: dolny limit to minQuantity (revert from iter2, zgodnie z user feedback).
    fun decrementParticipantCount() {
        val cur = _uiState.value
        val cls = cur.cartConfig?.ticketClasses?.firstOrNull { it.id == cur.selectedTicketClassId } ?: return
        val newCount = (cur.participantCount - 1).coerceAtLeast(cls.minQuantity)
        if (newCount == cur.participantCount) return
        val resized = resizeParticipants(cur.participantsData, newCount)
        val newSubStep = cur.participantSubStep.coerceAtMost(newCount - 1)
        _uiState.value = cur.copy(
            participantCount = newCount,
            participantsData = resized,
            participantSubStep = newSubStep,
            participantData = resized.getOrElse(newSubStep) { emptyMap() },
            errors = cur.errors - "quantity"
        )
    }

    private fun resizeParticipants(existing: List<Map<String, String>>, newSize: Int): List<Map<String, String>> {
        return when {
            newSize <= 0 -> listOf(emptyMap())
            existing.size == newSize -> existing
            existing.size > newSize -> existing.take(newSize)
            else -> existing + List(newSize - existing.size) { emptyMap() }
        }
    }

    fun updateParticipantField(id: String, value: String) {
        // WO-171: route przez updateParticipantFieldAt na aktywnym subStep.
        updateParticipantFieldAt(_uiState.value.participantSubStep, id, value)
    }

    // WO-171: aktualizacja pola dla konkretnego uczestnika.
    fun updateParticipantFieldAt(index: Int, id: String, value: String) {
        val cur = _uiState.value
        if (index !in cur.participantsData.indices) return
        val newList = cur.participantsData.toMutableList()
        newList[index] = newList[index] + (id to value)
        _uiState.value = cur.copy(
            participantsData = newList,
            participantData = if (index == cur.participantSubStep) newList[index] else cur.participantData,
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
        // WO-176: legacy alias — kopiuj z PIERWSZEGO uczestnika (gdy count == 1).
        copyParticipantToPayerAt(0)
    }

    // WO-176: kopiuj dane WYBRANEGO uczestnika (po indexie) na płatnika.
    // Gdy participantCount > 1 UI pokazuje dropdown z wyborem.
    fun copyParticipantToPayerAt(index: Int) {
        val s = _uiState.value
        val source = s.participantsData.getOrNull(index) ?: return
        val firstName = source["firstName"]?.trim().orEmpty()
        val lastName = source["lastName"]?.trim().orEmpty()
        val email = source["email"]?.trim().orEmpty()
        val phone = source["phone"]?.trim().orEmpty()
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
        // WO-171: subtotal = priceGross * participantCount, rabat aplikowany do subtotal.
        val s = _uiState.value
        val ticket = s.cartConfig?.ticketClasses?.firstOrNull { it.id == s.selectedTicketClassId }
            ?: return 0.0
        val subtotal = ticket.priceGross * s.participantCount
        val applied = s.appliedDiscount ?: return subtotal
        // Sprawdź czy kod dotyczy tego biletu
        if (applied.ticketClassIds.isNotEmpty() && ticket.id !in applied.ticketClassIds) {
            return subtotal
        }
        val discount = when (applied.type) {
            "fixed" -> applied.value
            else -> subtotal * (applied.percent / 100.0)
        }
        return maxOf(0.0, subtotal - discount)
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
            1 -> {
                if (s.selectedTicketClassId == null) {
                    errors["ticket"] = "Wybierz typ biletu"
                } else {
                    // WO-175: walidacja minQuantity — count musi być >= minimum klasy biletu.
                    val cls = s.cartConfig?.ticketClasses?.firstOrNull { it.id == s.selectedTicketClassId }
                    if (cls != null && s.participantCount < cls.minQuantity) {
                        errors["quantity"] = "Minimum ${cls.minQuantity} biletów dla tego typu"
                    }
                }
            }
            2 -> {
                // WO-171: walidacja dotyczy AKTUALNEGO subStep'a (current participant).
                // WO-176: usunięty HARD-OVERRIDE (firstName/lastName/email zawsze required).
                //         Trzymamy się 1:1 z koszykiem: walidujemy DOKŁADNIE to co backend
                //         zwrócił w participant_fields (z fallback do defaultParticipantFields).
                //         Pola i ich required flagi są zarządzane w panelu wydarzenia
                //         (purchase_cart_config.participant_fields) — ten sam source-of-truth
                //         co Purchase Cart.
                val fields = s.cartConfig?.participantFields?.takeIf { it.isNotEmpty() }
                    ?: defaultParticipantFields()
                val currentData = s.participantsData.getOrElse(s.participantSubStep) { emptyMap() }
                fields.filter { it.visible }.forEach { f ->
                    val v = currentData[f.id].orEmpty().trim()
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

        // WO-171: budujemy listę N uczestników z s.participantsData (limited do participantCount).
        val participantDtos = s.participantsData.take(s.participantCount).mapIndexed { idx, data ->
            val pFirst = data["firstName"]?.trim().orEmpty()
                .ifBlank { if (idx == 0) s.payer.firstName.trim() else "" }
            val pLast = data["lastName"]?.trim().orEmpty()
                .ifBlank { if (idx == 0) s.payer.lastName.trim() else "" }
            val pEmail = data["email"]?.trim().orEmpty()
                .ifBlank { if (idx == 0) s.payer.email.trim() else "" }
            val pPhone = data["phone"]?.trim()?.ifBlank { null }
            MobileCheckoutParticipantDto(
                firstName = pFirst,
                lastName = pLast,
                email = pEmail,
                phone = pPhone,
                ticketTypeId = ticketClass.id,
                ticketTypeName = ticketClass.name,
                customFields = data.filterKeys { it !in setOf("firstName", "lastName", "email", "phone") }
                    .takeIf { it.isNotEmpty() }
            )
        }

        val firstEmail = participantDtos.firstOrNull()?.email ?: s.payer.email
        val consentsMap = s.consentValues.mapValues { (_, v) -> v as Any }
        val discountTrim = s.discountCode.trim()
        val subtotalGross = ticketClass.priceGross * s.participantCount
        val finalGross = computeFinalGross()
        val summary: MutableMap<String, Any?> = mutableMapOf(
            "quantity" to s.participantCount,
            "subtotal_gross" to subtotalGross,
            "final_gross" to finalGross
        )
        if (discountTrim.isNotEmpty()) summary["discountCode"] = discountTrim

        val payload = MobileCheckoutPayloadDto(
            payer = payerDto,
            participants = participantDtos,
            summary = summary,
            consents = consentsMap.takeIf { it.isNotEmpty() }
        )

        val countSuffix = if (s.participantCount > 1) " (${s.participantCount} biletów)" else ""

        _uiState.value = s.copy(isSubmitting = true)
        viewModelScope.launch {
            try {
                when (s.paymentMethodId) {
                    "proforma" -> {
                        val r = api.checkoutProforma(eventId, payload)
                        handleResponse(r.isSuccessful, r.code(), r.body()?.eventOrderId,
                            r.errorBody()?.string(),
                            "Proforma wysłana do $firstEmail$countSuffix")
                    }
                    "stripe" -> {
                        val r = api.checkoutStripe(eventId, payload)
                        handleResponse(r.isSuccessful, r.code(), r.body()?.eventOrderId,
                            r.errorBody()?.string(),
                            "Email z linkiem wysłany do $firstEmail$countSuffix")
                    }
                    "free" -> {
                        val r = api.checkoutFree(eventId, payload)
                        handleResponse(r.isSuccessful, r.code(), r.body()?.eventOrderId,
                            r.errorBody()?.string(),
                            if (s.participantCount > 1) "Dodano ${s.participantCount} uczestników" else "Uczestnik dodany")
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
            Analytics.capture(
                AnalyticsEvent.ADD_ORDER_COMPLETED,
                mapOf(
                    AnalyticsEvent.Props.PAYMENT_METHOD to (_uiState.value.paymentMethodId ?: "unknown")
                )
            )
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
