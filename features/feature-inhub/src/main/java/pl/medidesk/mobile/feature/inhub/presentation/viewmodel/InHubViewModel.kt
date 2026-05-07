package pl.medidesk.mobile.feature.inhub.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.InHubConfig
import pl.medidesk.mobile.core.model.CheckinResult
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.InHubConfigRequest
import pl.medidesk.mobile.core.network.dto.VerifyPinRequest
import pl.medidesk.mobile.core.sync.CheckinUseCase
import javax.inject.Inject

enum class InHubMode { SETUP, PIN_LOCK, ACTIVE, RESULT }

data class InHubUiState(
    val mode: InHubMode = InHubMode.SETUP,
    val config: InHubConfig = InHubConfig(exists = false),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastCheckinResult: CheckinResult? = null,
    val pin: String = "",
    val isBypassed: Boolean = false // Added to handle server errors
)

@HiltViewModel
class InHubViewModel @Inject constructor(
    private val apiService: MobileApiService,
    private val checkinUseCase: CheckinUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(InHubUiState())
    val uiState: StateFlow<InHubUiState> = _uiState.asStateFlow()

    fun loadConfig(eventId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getInHubConfig(eventId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val config = InHubConfig(
                        exists = body.exists,
                        id = body.id,
                        eventId = body.eventId,
                        autoCheckin = body.autoCheckin ?: true,
                        showSearch = body.showSearch ?: true,
                        showWalkin = body.showWalkin ?: false
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        config = config,
                        mode = if (config.exists) InHubMode.PIN_LOCK else InHubMode.SETUP
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, mode = InHubMode.SETUP)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message, mode = InHubMode.SETUP)
            }
        }
    }

    fun saveConfig(eventId: String, pin: String, autoCheckin: Boolean, showSearch: Boolean, showWalkin: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.saveInHubConfig(
                    eventId, InHubConfigRequest(pin, autoCheckin, showSearch, showWalkin)
                )
                if (response.isSuccessful) {
                    loadConfig(eventId)
                } else {
                    val errorMsg = "Błąd serwera (${response.code()}). Możesz spróbować uruchomić tryb lokalny."
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Błąd połączenia: ${e.message}")
            }
        }
    }
    
    fun bypassAndStart() {
        // Force start even if server save failed
        _uiState.value = _uiState.value.copy(mode = InHubMode.ACTIVE, isBypassed = true, error = null)
    }

    fun verifyPin(eventId: String, pin: String) {
        if (_uiState.value.isBypassed) {
            _uiState.value = _uiState.value.copy(mode = InHubMode.ACTIVE)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.verifyPin(eventId, VerifyPinRequest(pin))
                val body = response.body()
                if (response.isSuccessful && body?.valid == true) {
                    _uiState.value = _uiState.value.copy(isLoading = false, mode = InHubMode.ACTIVE)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Nieprawidłowy PIN")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onQrScanned(ticketId: String, eventId: String) {
        viewModelScope.launch {
            val result = checkinUseCase(ticketId, eventId)
            _uiState.value = _uiState.value.copy(mode = InHubMode.RESULT, lastCheckinResult = result)
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(mode = InHubMode.ACTIVE, lastCheckinResult = null)
        }
    }

    fun lock() { _uiState.value = _uiState.value.copy(mode = InHubMode.PIN_LOCK) }
    fun onPinChanged(pin: String) { _uiState.value = _uiState.value.copy(pin = pin, error = null) }
}
