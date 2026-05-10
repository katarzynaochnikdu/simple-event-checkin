package pl.medidesk.mobile

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pl.medidesk.mobile.core.datastore.AuthDataStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    authDataStore: AuthDataStore
) : ViewModel() {
    val themePreference: StateFlow<String> = authDataStore.themePreferenceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
}
