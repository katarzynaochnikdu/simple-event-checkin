package pl.medidesk.mobile.feature.sponsors.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.EventSponsor
import pl.medidesk.mobile.core.network.MobileApiService
import javax.inject.Inject

data class SponsorsUiState(
    val isLoading: Boolean = true,
    val sponsors: List<EventSponsor> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
) {
    val filteredSponsors: List<EventSponsor> get() {
        if (searchQuery.isBlank()) return sponsors
        val q = searchQuery.lowercase()
        return sponsors.filter {
            it.companyName.lowercase().contains(q) ||
            it.companyNameShort.lowercase().contains(q) ||
            it.industryCategory.lowercase().contains(q)
        }
    }
}

@HiltViewModel
class SponsorsViewModel @Inject constructor(
    private val api: MobileApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""
    private val _uiState = MutableStateFlow(SponsorsUiState())
    val uiState: StateFlow<SponsorsUiState> = _uiState.asStateFlow()

    init { loadSponsors() }

    fun loadSponsors() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = api.getSponsors(eventId)
                if (response.isSuccessful) {
                    val body = response.body()
                    val sponsors = body?.sponsors?.map { dto ->
                        EventSponsor(
                            eventSponsorId = dto.eventSponsorId,
                            sponsorCompanyId = dto.sponsorCompanyId,
                            companyName = dto.companyName,
                            companyNameShort = dto.companyNameShort,
                            companyLogoUrl = dto.companyLogoUrl,
                            industryCategory = dto.industryCategory.orEmpty(),
                            packageLabel = dto.packageLabel,
                            packageColor = dto.packageColor,
                            pipelineStatus = dto.pipelineStatus.orEmpty(),
                            dealType = dto.dealType.orEmpty(),
                            contractValueNet = dto.contractValueNet,
                            tags = dto.tags.orEmpty()
                        )
                    } ?: emptyList()
                    _uiState.value = _uiState.value.copy(isLoading = false, sponsors = sponsors)
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
