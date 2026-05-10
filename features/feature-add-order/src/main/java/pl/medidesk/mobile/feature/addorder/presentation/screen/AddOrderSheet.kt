package pl.medidesk.mobile.feature.addorder.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import pl.medidesk.mobile.feature.addorder.presentation.component.ConsentsForm
import pl.medidesk.mobile.feature.addorder.presentation.component.ParticipantFieldsForm
import pl.medidesk.mobile.feature.addorder.presentation.component.PayerFieldsForm
import pl.medidesk.mobile.feature.addorder.presentation.component.PaymentMethodPicker
import pl.medidesk.mobile.feature.addorder.presentation.component.TicketTypePicker
import pl.medidesk.mobile.feature.addorder.presentation.state.AddOrderResult
import pl.medidesk.mobile.feature.addorder.presentation.state.TOTAL_STEPS
import pl.medidesk.mobile.feature.addorder.presentation.viewmodel.AddOrderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrderSheet(
    eventId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AddOrderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    LaunchedEffect(eventId) { viewModel.loadCartConfig(eventId) }

    LaunchedEffect(state.submitResult) {
        when (val r = state.submitResult) {
            is AddOrderResult.Success -> {
                Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                viewModel.consumeSubmitResult()
                onSuccess()
                onDismiss()
            }
            is AddOrderResult.Error -> {
                Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                viewModel.consumeSubmitResult()
            }
            null -> {}
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dodaj zamówienie", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.step}/$TOTAL_STEPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.step.toFloat() / TOTAL_STEPS },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            // Body
            when {
                state.isLoadingConfig -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.configError != null -> {
                    Text(state.configError ?: "Błąd",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp))
                    Button(onClick = { viewModel.loadCartConfig(eventId) }) {
                        Text("Spróbuj ponownie")
                    }
                }
                state.cartConfig != null -> {
                    val cfg = state.cartConfig!!
                    Column(modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())) {
                        when (state.step) {
                            1 -> TicketTypePicker(
                                ticketClasses = cfg.ticketClasses,
                                selectedId = state.selectedTicketClassId,
                                onSelect = viewModel::selectTicketClass,
                                error = state.errors["ticket"]
                            )
                            2 -> ParticipantFieldsForm(
                                fields = cfg.participantFields,
                                values = state.participantData,
                                errors = state.errors,
                                onFieldChange = viewModel::updateParticipantField
                            )
                            3 -> PayerFieldsForm(
                                payer = state.payer,
                                errors = state.errors,
                                isLookingUpGus = state.isLookingUpGus,
                                gusError = state.gusError,
                                onPayerChange = viewModel::updatePayer,
                                onLookupGus = viewModel::lookupGus,
                                onCopyFromParticipant = viewModel::copyParticipantToPayer
                            )
                            4 -> ConsentsForm(
                                consentsConfig = cfg.consents,
                                values = state.consentValues,
                                errors = state.errors,
                                onToggle = viewModel::toggleConsent
                            )
                            5 -> PaymentMethodPicker(
                                methods = cfg.paymentMethods,
                                selectedId = state.paymentMethodId,
                                onSelect = viewModel::selectPaymentMethod,
                                discountCode = state.discountCode,
                                onDiscountCodeChange = viewModel::updateDiscountCode,
                                error = state.errors["payment"]
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Footer
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { if (state.step > 1) viewModel.prevStep() else onDismiss() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSubmitting
                        ) {
                            Text(if (state.step > 1) "Wstecz" else "Anuluj")
                        }
                        if (state.step < TOTAL_STEPS) {
                            Button(onClick = { viewModel.nextStep() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSubmitting) {
                                Text("Dalej")
                            }
                        } else {
                            Button(onClick = { viewModel.submit(eventId) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isSubmitting) {
                                if (state.isSubmitting) {
                                    CircularProgressIndicator(strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text("Wyślij")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
