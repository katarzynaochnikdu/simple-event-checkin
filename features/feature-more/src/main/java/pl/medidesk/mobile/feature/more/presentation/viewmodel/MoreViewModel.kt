package pl.medidesk.mobile.feature.more.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.model.SyncState
import pl.medidesk.mobile.core.sync.SyncEngine
import javax.inject.Inject

data class MoreUiState(
    val userEmail: String = "",
    val userDisplayName: String = "",
    val syncState: SyncState = SyncState()
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoreUiState())
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authDataStore.userEmailFlow,
                authDataStore.userRoleFlow, // Temporarily using role instead of name to fix build
                syncEngine.syncState
            ) { email: String?, role: String?, sync: SyncState ->
                MoreUiState(
                    userEmail = email ?: "",
                    userDisplayName = role ?: "",
                    syncState = sync
                )
            }.collect { _uiState.value = it }
        }
    }

    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            authDataStore.clearAll()
            syncEngine.stopPeriodicSync()
            onLogout()
        }
    }
}
