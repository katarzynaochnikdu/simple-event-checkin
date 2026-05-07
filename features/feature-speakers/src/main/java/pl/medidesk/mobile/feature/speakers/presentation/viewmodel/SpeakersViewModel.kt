package pl.medidesk.mobile.feature.speakers.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.Speaker
import pl.medidesk.mobile.core.network.MobileApiService
import javax.inject.Inject

data class SpeakersUiState(
    val isLoading: Boolean = true,
    val speakers: List<Speaker> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
) {
    val filteredSpeakers: List<Speaker> get() {
        if (searchQuery.isBlank()) return speakers
        val q = searchQuery.lowercase()
        return speakers.filter {
            it.firstName.lowercase().contains(q) ||
            it.lastName.lowercase().contains(q) ||
            it.organization.lowercase().contains(q) ||
            it.affiliation.lowercase().contains(q)
        }
    }
}

@HiltViewModel
class SpeakersViewModel @Inject constructor(
    private val api: MobileApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""
    private val _uiState = MutableStateFlow(SpeakersUiState())
    val uiState: StateFlow<SpeakersUiState> = _uiState.asStateFlow()

    init { loadSpeakers() }

    fun loadSpeakers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.getSpeakers(eventId)
                if (response.isSuccessful) {
                    val body = response.body()
                    val speakers = body?.speakers?.map { dto ->
                        Speaker(
                            speakerId = dto.speakerId,
                            firstName = dto.firstName,
                            lastName = dto.lastName,
                            title = dto.title.orEmpty(),
                            affiliation = dto.affiliation.orEmpty(),
                            organization = dto.organization.orEmpty(),
                            photoUrl = dto.photoUrl.orEmpty(),
                            bio = dto.bio.orEmpty(),
                            email = dto.email.orEmpty(),
                            socialLinkedin = dto.socialLinkedin.orEmpty()
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(isLoading = false, speakers = speakers)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Błąd sieci")
            }
        }
    }

    fun onSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
}
