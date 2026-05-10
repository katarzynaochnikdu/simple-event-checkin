package pl.medidesk.mobile.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.ResetPasswordRequest
import javax.inject.Inject

data class ResetPasswordUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val tokenInvalid: Boolean = false
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val apiService: MobileApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onNewPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = null)
    }

    fun submit(token: String) {
        val state = _uiState.value
        if (token.isBlank()) {
            _uiState.value = state.copy(tokenInvalid = true, error = "Brak tokenu — otwórz link z emaila ponownie")
            return
        }
        if (state.newPassword.length < 8) {
            _uiState.value = state.copy(error = "Hasło musi mieć minimum 8 znaków")
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.value = state.copy(error = "Hasła nie są identyczne")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val resp = apiService.resetPassword(
                    ResetPasswordRequest(token = token, newPassword = state.newPassword)
                )
                _uiState.value = if (resp.isSuccessful && resp.body()?.success == true) {
                    state.copy(isLoading = false, isSuccess = true)
                } else {
                    val errMsg = resp.body()?.error ?: when (resp.code()) {
                        400 -> "Token nieprawidłowy lub wygasł"
                        else -> "Nie udało się zmienić hasła (kod ${resp.code()})"
                    }
                    val tokenBad = errMsg.contains("Token", ignoreCase = true)
                    state.copy(isLoading = false, error = errMsg, tokenInvalid = tokenBad)
                }
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = e.message ?: "Błąd połączenia"
                )
            }
        }
    }
}
