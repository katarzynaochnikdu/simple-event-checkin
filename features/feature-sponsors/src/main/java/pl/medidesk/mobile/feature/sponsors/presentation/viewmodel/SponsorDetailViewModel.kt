package pl.medidesk.mobile.feature.sponsors.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.model.*
import pl.medidesk.mobile.core.network.MobileApiService
import javax.inject.Inject

data class SponsorDetailUiState(
    val isLoading: Boolean = true,
    val detail: SponsorDetail? = null,
    val error: String? = null
)

@HiltViewModel
class SponsorDetailViewModel @Inject constructor(
    private val api: MobileApiService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""
    private val eventSponsorId: Long = savedStateHandle["eventSponsorId"] ?: 0L
    private val _uiState = MutableStateFlow(SponsorDetailUiState())
    val uiState: StateFlow<SponsorDetailUiState> = _uiState.asStateFlow()

    init { loadDetail() }

    private fun loadDetail() {
        viewModelScope.launch {
            try {
                val response = api.getSponsorDetail(eventId, eventSponsorId)
                if (response.isSuccessful) {
                    val dto = response.body()
                    if (dto != null) {
                        _uiState.value = SponsorDetailUiState(
                            isLoading = false,
                            detail = SponsorDetail(
                                eventSponsorId = dto.eventSponsorId,
                                company = SponsorCompany(
                                    id = dto.company.id,
                                    name = dto.company.name,
                                    nameShort = dto.company.nameShort,
                                    nip = dto.company.nip.orEmpty(),
                                    industryCategory = dto.company.industryCategory.orEmpty(),
                                    website = dto.company.website.orEmpty(),
                                    emailGeneral = dto.company.emailGeneral.orEmpty(),
                                    phoneGeneral = dto.company.phoneGeneral.orEmpty(),
                                    logoUrl = dto.company.logoUrl,
                                    addressCity = dto.company.addressCity.orEmpty(),
                                    addressStreet = dto.company.addressStreet.orEmpty(),
                                    addressPostalCode = dto.company.addressPostalCode.orEmpty(),
                                    cooperationStatus = dto.company.cooperationStatus.orEmpty()
                                ),
                                packageLabel = dto.packageLabel,
                                packageColor = dto.packageColor,
                                pipelineStatus = dto.pipelineStatus.orEmpty(),
                                dealType = dto.dealType.orEmpty(),
                                contractValueNet = dto.contractValueNet,
                                opsStatus = dto.opsStatus.orEmpty(),
                                tags = dto.tags.orEmpty(),
                                contacts = dto.contacts?.map {
                                    ContactPerson(
                                        id = it.id,
                                        firstName = it.firstName,
                                        lastName = it.lastName,
                                        email = it.email.orEmpty(),
                                        phone = it.phone.orEmpty(),
                                        position = it.position.orEmpty(),
                                        department = it.department.orEmpty()
                                    )
                                } ?: emptyList(),
                                benefits = dto.benefits?.map {
                                    SponsorBenefit(
                                        name = it.name,
                                        status = it.status.orEmpty(),
                                        category = it.category.orEmpty()
                                    )
                                } ?: emptyList()
                            )
                        )
                    } else {
                        _uiState.value = SponsorDetailUiState(isLoading = false, error = "Brak danych")
                    }
                } else {
                    _uiState.value = SponsorDetailUiState(isLoading = false, error = "Błąd: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = SponsorDetailUiState(isLoading = false, error = e.message)
            }
        }
    }
}
