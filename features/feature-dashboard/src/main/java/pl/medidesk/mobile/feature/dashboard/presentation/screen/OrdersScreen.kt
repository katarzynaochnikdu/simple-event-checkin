package pl.medidesk.mobile.feature.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.OrderDto
import pl.medidesk.mobile.core.ui.theme.StatusColors
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class OrdersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val orders: List<OrderDto> = emptyList(),
    val totalOrders: Int = 0,
    val paidCount: Int = 0,
    val totalRevenue: Double = 0.0,
    val currency: String = "PLN"
)

data class ActionUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val api: MobileApiService
) : ViewModel() {
    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow(ActionUiState())
    val actionState = _actionState.asStateFlow()

    fun load(eventId: String) {
        viewModelScope.launch {
            _uiState.value = OrdersUiState(isLoading = true)
            try {
                val response = api.getOrders(eventId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.value = OrdersUiState(
                        isLoading = false,
                        orders = body.orders,
                        totalOrders = body.totalOrders,
                        paidCount = body.paidCount,
                        totalRevenue = body.totalRevenue,
                        currency = body.currency
                    )
                } else {
                    _uiState.value = OrdersUiState(isLoading = false, error = "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = OrdersUiState(isLoading = false, error = e.message ?: "Nieznany błąd")
            }
        }
    }

    fun changeOrderStatus(orderId: String, newStatus: String, eventId: String) {
        viewModelScope.launch {
            _actionState.value = ActionUiState(isLoading = true)
            try {
                val request = pl.medidesk.mobile.core.network.dto.OrderStatusUpdateRequest(status = newStatus)
                val response = api.updateOrderStatus(orderId, request)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    _actionState.value = ActionUiState(successMessage = "Zmieniono status pomyślnie")
                    load(eventId) // odśwież dane
                } else {
                    _actionState.value = ActionUiState(error = body?.error ?: "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _actionState.value = ActionUiState(error = e.message ?: "Nieznany błąd")
            }
        }
    }

    fun resendTickets(orderId: String) {
        viewModelScope.launch {
            _actionState.value = ActionUiState(isLoading = true)
            try {
                val response = api.resendTickets(orderId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    _actionState.value = ActionUiState(successMessage = "Bilety wysłane ponownie")
                } else {
                    _actionState.value = ActionUiState(error = body?.error ?: "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _actionState.value = ActionUiState(error = e.message ?: "Nieznany błąd")
            }
        }
    }

    fun sendReminder(orderId: String) {
        viewModelScope.launch {
            _actionState.value = ActionUiState(isLoading = true)
            try {
                val response = api.sendReminder(orderId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    _actionState.value = ActionUiState(successMessage = "Przypomnienie wysłane")
                } else {
                    _actionState.value = ActionUiState(error = body?.error ?: "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _actionState.value = ActionUiState(error = e.message ?: "Nieznany błąd")
            }
        }
    }

    fun clearActionState() {
        _actionState.value = ActionUiState()
    }
}

// ─── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    viewModel: OrdersViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(eventId) { viewModel.load(eventId) }

    LaunchedEffect(actionState.successMessage, actionState.error) {
        actionState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionState()
        }
        actionState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearActionState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Zamówienia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Text(
                        "${state.totalOrders} zam.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.load(eventId) }) { Text("Ponów") }
                    }
                }
                state.orders.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Brak zamówień", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Summary header
                        item {
                            SummaryRow(state)
                            Spacer(Modifier.height(8.dp))
                        }
                        items(state.orders, key = { it.eventOrderId }) { order ->
                            OrderCard(
                                order = order,
                                onChangeStatus = { newStatus -> viewModel.changeOrderStatus(order.eventOrderId, newStatus, eventId) },
                                onResendTicket = { viewModel.resendTickets(order.eventOrderId) },
                                onSendReminder = { viewModel.sendReminder(order.eventOrderId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Summary ───────────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(state: OrdersUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SumCard("Przychód", "%.2f %s".format(state.totalRevenue, state.currency), StatusColors.Paid, Modifier.weight(1f))
        SumCard("Opłacone", "${state.paidCount}/${state.totalOrders}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun SumCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = color.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 16.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Order Card ────────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(
    order: OrderDto,
    onChangeStatus: (String) -> Unit,
    onResendTicket: () -> Unit,
    onSendReminder: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    if (showStatusDialog) {
        val statuses = listOf(
            "received" to "Otrzymane",
            "pending_payment" to "Oczekuje na płatność",
            "paid" to "Opłacone",
            "cancelled" to "Anulowane",
            "refunded" to "Zwrócone"
        )
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Zmień status") },
            text = {
                Column {
                    statuses.forEach { (srv, displayName) ->
                        TextButton(
                            onClick = {
                                onChangeStatus(srv)
                                showStatusDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text(displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusDialog = false }) { Text("Anuluj") }
            }
        )
    }

    val statusColor = when (order.status) {
        "paid" -> StatusColors.Paid
        "received" -> StatusColors.Pending
        "pending_payment" -> StatusColors.Pending
        "cancelled" -> StatusColors.Cancelled
        "failed" -> StatusColors.Cancelled
        else -> StatusColors.Neutral
    }
    val statusLabel = when (order.status) {
        "paid" -> "Opłacone"
        "received" -> "Otrzymane"
        "pending_payment" -> "Oczekuje"
        "cancelled" -> "Anulowane"
        "failed" -> "Niepowodzenie"
        else -> order.status
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top: name + status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.purchaserName?.ifBlank { order.purchaserEmail ?: "—" } ?: "—",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    val company = order.purchaserCompany
                    if (!company.isNullOrBlank()) {
                        Text(company, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(statusLabel, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold)
                }

                // 3-dot menu actions
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                        Icon(Icons.Default.MoreVert, "Opcje", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Zmień status") },
                            onClick = {
                                expanded = false
                                showStatusDialog = true
                            }
                        )
                        if (order.status == "paid") {
                            DropdownMenuItem(
                                text = { Text("Wyślij bilety ponownie") },
                                onClick = {
                                    expanded = false
                                    onResendTicket()
                                }
                            )
                        }
                        if (order.status == "pending_payment" || order.status == "received") {
                            DropdownMenuItem(
                                text = { Text("Wyślij przypomnienie") },
                                onClick = {
                                    expanded = false
                                    onSendReminder()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom: amount + participants + date
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Amount
                Text(
                    "%.2f %s".format(order.total, order.currency),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                // Participants
                Icon(Icons.Default.People, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(3.dp))
                Text("${order.participantCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (order.checkedInCount > 0) {
                    Text(" (${order.checkedInCount}✓)", fontSize = 11.sp, color = StatusColors.Paid)
                }
                Spacer(Modifier.weight(1f))
                // Date
                val dateShort = order.createdAt?.take(10) ?: ""
                Text(dateShort, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Payment method / promo
            val pMethod = order.paymentMethod
            val promo = order.promoCode
            if (!pMethod.isNullOrBlank() || !promo.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row {
                    if (!pMethod.isNullOrBlank()) {
                        Text("💳 $pMethod", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!promo.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("🏷️ $promo", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}
