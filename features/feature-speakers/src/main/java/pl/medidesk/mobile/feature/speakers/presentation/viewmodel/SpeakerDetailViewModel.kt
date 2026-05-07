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

data class SpeakerDetailUiState(
    val isLoading: Boolean = true,
    val speaker: Speaker? = null,
    val error: String? = null
)

@HiltViewModel
class SpeakerDetailViewModel @Inject constructor(
    private val api: MobileApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""
    private val speakerId: String = savedStateHandle["speakerId"] ?: ""
    private val _uiState = MutableStateFlow(SpeakerDetailUiState())
    val uiState: StateFlow<SpeakerDetailUiState> = _uiState.asStateFlow()

    init { loadDetail() }

    private fun loadDetail() {
        viewModelScope.launch {
            try {
                val response = api.getSpeakerDetail(eventId, speakerId)
                if (response.isSuccessful) {
                    val dto = response.body()
                    if (dto != null) {
                        _uiState.value = SpeakerDetailUiState(
                            isLoading = false,
                            speaker = Speaker(
                                speakerId = dto.speakerId,
                                firstName = dto.firstName,
                                lastName = dto.lastName,
                                title = dto.title.orEmpty(),
                                affiliation = dto.affiliation.orEmpty(),
                                organization = dto.organization.orEmpty(),
                                photoUrl = dto.photoUrl.orEmpty(),
                                bio = dto.bio.orEmpty(),
                                bioLong = dto.bioLong.orEmpty(),
                                email = dto.email.orEmpty(),
                                phone = dto.phone.orEmpty(),
                                socialLinkedin = dto.socialLinkedin.orEmpty(),
                                socialTwitter = dto.socialTwitter.orEmpty(),
                                website = dto.website.orEmpty(),
                                academicTitle = dto.academicTitle.orEmpty()
                            )
                        )
                    } else {
                        _uiState.value = SpeakerDetailUiState(isLoading = false, error = "Brak danych")
                    }
                } else {
                    _uiState.value = SpeakerDetailUiState(isLoading = false, error = "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = SpeakerDetailUiState(isLoading = false, error = e.message)
            }
        }
    }
}
