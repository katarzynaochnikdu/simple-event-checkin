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

data class SpeakersUiState(
    val isLoading: Boolean = true,
    val speakers: List<Speaker> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    // WO-MOB-015: check-in state
    val checkedInSpeakerIds: Set<String> = emptySet(),
    val pendingSpeakerIds: Set<String> = emptySet(),
    val onlyAbsentFilter: Boolean = false,
    val total: Int = 0,
    val attended: Int = 0,
    val undoConfirmSpeakerId: String? = null,
    val snackbarMessage: String? = null
) {
    /**
     * Final list applied to LazyColumn — search query + "tylko nieobecni" filter combined.
     */
    val visibleSpeakers: List<Speaker> get() {
        val baseList = if (searchQuery.isBlank()) speakers else {
            val q = searchQuery.lowercase()
            speakers.filter {
                it.firstName.lowercase().contains(q) ||
                it.lastName.lowercase().contains(q) ||
                it.organization.lowercase().contains(q) ||
                it.affiliation.lowercase().contains(q)
            }
        }
        return if (onlyAbsentFilter) {
            baseList.filterNot { it.speakerId in checkedInSpeakerIds }
        } else baseList
    }
}

@HiltViewModel
class SpeakersViewModel @Inject constructor(
    private val api: MobileApiService,
    private val checkinRepository: SpeakerCheckinRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""
    private val _uiState = MutableStateFlow(SpeakersUiState())
    val uiState: StateFlow<SpeakersUiState> = _uiState.asStateFlow()

    init {
        loadSpeakers()
        refreshStats()
    }

    fun loadSpeakers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            speakers = speakers,
                            total = speakers.size.coerceAtLeast(it.total)
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Blad: ${response.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Blad sieci") }
            }
        }
    }

    /**
     * Pull current attendance state from /speakers/checkin-stats. Used:
     *   - on init (cold start)
     *   - after each successful markAttended/undoAttended (reconciliation)
     *   - on manual refresh
     */
    fun refreshStats() {
        viewModelScope.launch {
            val result = checkinRepository.getStats(eventId)
            result.onSuccess { stats ->
                _uiState.update {
                    it.copy(
                        total = stats.total,
                        attended = stats.attended,
                        checkedInSpeakerIds = stats.attendedSpeakerIds.toSet()
                    )
                }
            }
            // On failure: keep last known state (no surprise loss of checkedIn marks for user).
        }
    }

    fun onSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleOnlyAbsentFilter() {
        _uiState.update { it.copy(onlyAbsentFilter = !it.onlyAbsentFilter) }
    }

    /**
     * Optimistic tap → repo → success or rollback.
     */
    fun markAttended(speakerId: String) {
        val current = _uiState.value
        if (speakerId in current.checkedInSpeakerIds || speakerId in current.pendingSpeakerIds) return

        _uiState.update {
            it.copy(
                pendingSpeakerIds = it.pendingSpeakerIds + speakerId,
                // optimistic: pre-add to checked set so UI shows checkbox tick instantly
                checkedInSpeakerIds = it.checkedInSpeakerIds + speakerId,
                attended = (it.attended + 1).coerceAtMost(it.total.coerceAtLeast(it.attended + 1))
            )
        }

        viewModelScope.launch {
            val result = checkinRepository.markAttended(eventId, speakerId)
            handlePostAction(speakerId, result, intendedCheckedIn = true)
        }
    }

    fun requestUndoConfirm(speakerId: String) {
        _uiState.update { it.copy(undoConfirmSpeakerId = speakerId) }
    }

    fun dismissUndoConfirm() {
        _uiState.update { it.copy(undoConfirmSpeakerId = null) }
    }

    fun undoAttended(speakerId: String) {
        _uiState.update {
            it.copy(
                undoConfirmSpeakerId = null,
                pendingSpeakerIds = it.pendingSpeakerIds + speakerId,
                checkedInSpeakerIds = it.checkedInSpeakerIds - speakerId,
                attended = (it.attended - 1).coerceAtLeast(0)
            )
        }

        viewModelScope.launch {
            val result = checkinRepository.undoAttended(eventId, speakerId)
            handlePostAction(speakerId, result, intendedCheckedIn = false)
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun handlePostAction(
        speakerId: String,
        result: SpeakerCheckinRepository.Result,
        intendedCheckedIn: Boolean
    ) {
        when (result) {
            is SpeakerCheckinRepository.Result.Success -> {
                _uiState.update {
                    it.copy(
                        pendingSpeakerIds = it.pendingSpeakerIds - speakerId,
                        snackbarMessage = if (result.isOffline) "Zapisano lokalnie — wyslemy po polaczeniu" else null
                    )
                }
                // Reconcile counts/state with server snapshot (covers race conditions
                // and duplicate-flag corrections).
                refreshStats()
            }
            is SpeakerCheckinRepository.Result.NotFound -> {
                rollbackOptimistic(speakerId, intendedCheckedIn)
                _uiState.update {
                    it.copy(snackbarMessage = "Prelegent nie istnieje w tym wydarzeniu")
                }
            }
            is SpeakerCheckinRepository.Result.Failure -> {
                rollbackOptimistic(speakerId, intendedCheckedIn)
                _uiState.update {
                    it.copy(snackbarMessage = result.message)
                }
            }
        }
    }

    /**
     * Revert the optimistic UI mutation: if we set checked, unset it (and vice versa).
     */
    private fun rollbackOptimistic(speakerId: String, intendedCheckedIn: Boolean) {
        _uiState.update {
            val newCheckedIds = if (intendedCheckedIn) it.checkedInSpeakerIds - speakerId
                else it.checkedInSpeakerIds + speakerId
            it.copy(
                pendingSpeakerIds = it.pendingSpeakerIds - speakerId,
                checkedInSpeakerIds = newCheckedIds,
                attended = newCheckedIds.size
            )
        }
    }
}
