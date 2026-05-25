package pl.medidesk.mobile.feature.speakers.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.Speaker
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.feature.speakers.data.repository.SpeakerCheckinRepository
import javax.inject.Inject

data class SpeakerDetailUiState(
    val isLoading: Boolean = true,
    val speaker: Speaker? = null,
    val error: String? = null,
    // WO-MOB-015 check-in state
    val isCheckedIn: Boolean = false,
    val attendedAt: String? = null,
    val isPending: Boolean = false,
    val showUndoDialog: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SpeakerDetailViewModel @Inject constructor(
    private val api: MobileApiService,
    private val checkinRepository: SpeakerCheckinRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""
    private val speakerId: String = savedStateHandle["speakerId"] ?: ""
    private val _uiState = MutableStateFlow(SpeakerDetailUiState())
    val uiState: StateFlow<SpeakerDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        loadCheckinState()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            try {
                val response = api.getSpeakerDetail(eventId, speakerId)
                if (response.isSuccessful) {
                    val dto = response.body()
                    if (dto != null) {
                        _uiState.update {
                            it.copy(
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
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Brak danych") }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Blad: ${response.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadCheckinState() {
        viewModelScope.launch {
            val result = checkinRepository.getStats(eventId)
            result.onSuccess { stats ->
                _uiState.update {
                    it.copy(isCheckedIn = speakerId in stats.attendedSpeakerIds)
                }
            }
        }
    }

    fun markAttended() {
        if (_uiState.value.isPending || _uiState.value.isCheckedIn) return
        _uiState.update { it.copy(isPending = true, isCheckedIn = true) }
        viewModelScope.launch {
            val result = checkinRepository.markAttended(eventId, speakerId)
            handlePostAction(result, intendedCheckedIn = true)
        }
    }

    fun requestUndoConfirm() {
        _uiState.update { it.copy(showUndoDialog = true) }
    }

    fun dismissUndoConfirm() {
        _uiState.update { it.copy(showUndoDialog = false) }
    }

    fun undoAttended() {
        if (_uiState.value.isPending || !_uiState.value.isCheckedIn) {
            _uiState.update { it.copy(showUndoDialog = false) }
            return
        }
        _uiState.update { it.copy(showUndoDialog = false, isPending = true, isCheckedIn = false) }
        viewModelScope.launch {
            val result = checkinRepository.undoAttended(eventId, speakerId)
            handlePostAction(result, intendedCheckedIn = false)
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun handlePostAction(
        result: SpeakerCheckinRepository.Result,
        intendedCheckedIn: Boolean
    ) {
        when (result) {
            is SpeakerCheckinRepository.Result.Success -> {
                _uiState.update {
                    it.copy(
                        isPending = false,
                        isCheckedIn = intendedCheckedIn,
                        attendedAt = if (intendedCheckedIn) result.attendedAt else null,
                        snackbarMessage = if (result.isOffline) "Zapisano lokalnie — wyslemy po polaczeniu" else null
                    )
                }
            }
            is SpeakerCheckinRepository.Result.NotFound -> {
                _uiState.update {
                    it.copy(
                        isPending = false,
                        isCheckedIn = !intendedCheckedIn,
                        snackbarMessage = "Prelegent nie istnieje w tym wydarzeniu"
                    )
                }
            }
            is SpeakerCheckinRepository.Result.Failure -> {
                _uiState.update {
                    it.copy(
                        isPending = false,
                        isCheckedIn = !intendedCheckedIn,
                        snackbarMessage = result.message
                    )
                }
            }
        }
    }
}
