package pl.medidesk.mobile.feature.more.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.ChangePasswordRequest
import javax.inject.Inject

data class SettingsUiState(
    val userEmail: String = "",
    val userDisplayName: String = "",
    val userRole: String = "",
    val themePreference: String = "SYSTEM",
    val isChangingPassword: Boolean = false,
    val passwordChangeSuccess: Boolean = false,
    val passwordChangeError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val apiService: MobileApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authDataStore.userEmailFlow,
                authDataStore.userFirstNameFlow,
                authDataStore.userLastNameFlow,
                authDataStore.userRoleFlow,
                authDataStore.themePreferenceFlow
            ) { email, firstName, lastName, role, theme ->
                val displayName = listOfNotNull(firstName, lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { email ?: "" }
                SettingsUiState(
                    userEmail = email ?: "",
                    userDisplayName = displayName,
                    userRole = role ?: "",
                    themePreference = theme
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            authDataStore.saveThemePreference(theme)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, passwordChangeError = null, passwordChangeSuccess = false) }
            try {
                val response = apiService.changePassword(ChangePasswordRequest(currentPassword, newPassword))
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    _uiState.update { it.copy(isChangingPassword = false, passwordChangeSuccess = true) }
                } else {
                    _uiState.update { it.copy(isChangingPassword = false, passwordChangeError = body?.error ?: "Błąd zmiany hasła") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isChangingPassword = false, passwordChangeError = e.message ?: "Błąd połączenia") }
            }
        }
    }

    fun clearPasswordState() {
        _uiState.update { it.copy(passwordChangeSuccess = false, passwordChangeError = null) }
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            authDataStore.clearAll()
            onLogout()
        }
    }
}
