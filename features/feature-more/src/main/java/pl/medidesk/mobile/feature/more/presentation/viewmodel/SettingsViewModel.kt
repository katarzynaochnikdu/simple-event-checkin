package pl.medidesk.mobile.feature.more.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.analytics.Analytics
import pl.medidesk.mobile.core.analytics.AnalyticsEvent
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.ChangePasswordRequest
import pl.medidesk.mobile.core.sync.LogoutUseCase
import javax.inject.Inject

data class SettingsUiState(
    val userEmail: String = "",
    val userDisplayName: String = "",
    val userRole: String = "",
    val themePreference: String = "SYSTEM",
    val analyticsConsent: Boolean = false,
    val isChangingPassword: Boolean = false,
    val passwordChangeSuccess: Boolean = false,
    val passwordChangeError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val apiService: MobileApiService,
    private val logoutUseCase: LogoutUseCase
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
                authDataStore.themePreferenceFlow,
                authDataStore.analyticsConsentFlow
            ) { values ->
                val email = values[0] as String?
                val firstName = values[1] as String?
                val lastName = values[2] as String?
                val role = values[3] as String?
                val theme = values[4] as String? ?: "SYSTEM"
                val consent = values[5] as Boolean?
                val displayName = listOfNotNull(firstName, lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { email ?: "" }
                SettingsUiState(
                    userEmail = email ?: "",
                    userDisplayName = displayName,
                    userRole = role ?: "",
                    themePreference = theme,
                    analyticsConsent = consent == true
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

    fun setAnalyticsConsent(consent: Boolean) {
        viewModelScope.launch {
            authDataStore.saveAnalyticsConsent(consent)
            // WO-MOB-033 (finding F2B-007 residual a): event audytowy zgody MUSI być
            // wysłany, gdy SDK jest opted-IN — inaczej PostHog go odrzuca i audit trail
            // zmian zgody jest niewiarygodny (TELEMETRY.md §… deklaruje wysyłkę).
            // - opt-OUT: capture PRZED optOut() (SDK jeszcze opted-in → event przechodzi).
            // - opt-IN:  optIn() PRZED capture() (SDK startuje opted-out → najpierw włącz).
            if (consent) {
                Analytics.optIn()
                Analytics.capture(
                    AnalyticsEvent.ANALYTICS_CONSENT_CHANGED,
                    mapOf(AnalyticsEvent.Props.ACTION to "opted_in")
                )
            } else {
                Analytics.capture(
                    AnalyticsEvent.ANALYTICS_CONSENT_CHANGED,
                    mapOf(AnalyticsEvent.Props.ACTION to "opted_out")
                )
                Analytics.optOut()
            }
        }
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            // Capture PRZED use casem — event ma być jeszcze przypisany do wylogowywanego
            // usera (Analytics.reset() wykonuje się wewnątrz LogoutUseCase).
            Analytics.capture(AnalyticsEvent.USER_LOGGED_OUT)
            // WO-MOB-028 (F2A-001): pełny wipe lokalnego cache'u PII (Room clearAllTables +
            // encrypted prefs). Manual logout → best-effort flush pending kolejek (≤5 s).
            logoutUseCase(flushPendingQueues = true)
            onLogout()
        }
    }
}
