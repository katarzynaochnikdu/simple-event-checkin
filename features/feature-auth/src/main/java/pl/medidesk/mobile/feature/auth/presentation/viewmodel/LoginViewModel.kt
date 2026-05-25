package pl.medidesk.mobile.feature.auth.presentation.viewmodel

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
import pl.medidesk.mobile.core.network.dto.ForgotPasswordRequest
import pl.medidesk.mobile.core.sync.ParticipantTagsRepository
import pl.medidesk.mobile.feature.auth.domain.usecase.LoginUseCase
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val mustChangePassword: Boolean = false,
    val forgotPasswordSent: Boolean = false,
    val forgotPasswordLoading: Boolean = false,
    val forgotPasswordError: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val apiService: MobileApiService,
    private val participantTagsRepository: ParticipantTagsRepository
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
                val user = result.getOrNull()
                // Identify user — only userId + role (no PII like email/name)
                user?.let { Analytics.identify(userId = it.id.toString(), role = it.role) }
                Analytics.capture(AnalyticsEvent.USER_LOGGED_IN, mapOf(AnalyticsEvent.Props.ROLE to (user?.role ?: "")))
                // WO-MOB-016: fire-and-forget refresh kanonicznych definicji tagow
                // (label_pl + kolory chipow). Fail-soft - repo zostawia defaults.
                viewModelScope.launch { participantTagsRepository.refresh() }
                state.copy(isLoading = false, isSuccess = true, mustChangePassword = user?.mustChangePassword == true)
            } else {
                state.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Błąd logowania")
            }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(forgotPasswordError = "Podaj adres e-mail")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(forgotPasswordLoading = true, forgotPasswordError = null)
            try {
                val resp = apiService.forgotPassword(ForgotPasswordRequest(email.trim()))
                _uiState.value = if (resp.isSuccessful) {
                    _uiState.value.copy(forgotPasswordLoading = false, forgotPasswordSent = true)
                } else {
                    _uiState.value.copy(forgotPasswordLoading = false, forgotPasswordSent = true)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    forgotPasswordLoading = false,
                    forgotPasswordSent = true
                )
            }
        }
    }

    fun clearForgotPasswordState() {
        _uiState.value = _uiState.value.copy(
            forgotPasswordSent = false,
            forgotPasswordLoading = false,
            forgotPasswordError = null
        )
    }
}
