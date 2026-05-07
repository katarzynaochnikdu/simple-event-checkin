package pl.medidesk.mobile.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.feature.auth.domain.usecase.LoginUseCase
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) { _uiState.value = _uiState.value.copy(email = email, error = null) }
    fun onPasswordChange(password: String) { _uiState.value = _uiState.value.copy(password = password, error = null) }

    fun login() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = loginUseCase(state.email, state.password)
            _uiState.value = if (result.isSuccess) {
                state.copy(isLoading = false, isSuccess = true)
            } else {
                state.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Błąd logowania")
            }
        }
    }
}
