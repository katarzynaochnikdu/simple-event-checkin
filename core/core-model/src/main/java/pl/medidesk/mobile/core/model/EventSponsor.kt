package pl.medidesk.mobile.core.model

data class EventSponsor(
    val eventSponsorId: Long,
    val sponsorCompanyId: Long,
    val companyName: String,
    val companyNameShort: String,
    val companyLogoUrl: String? = null,
    val industryCategory: String = "",
    val packageLabel: String? = null,
    val packageColor: String? = null,
    val pipelineStatus: String = "",
    val dealType: String = "",
    val contractValueNet: Double? = null,
    val tags: List<String> = emptyList()
) {
    val initials: String get() = companyNameShort.take(2).uppercase()
}

data class SponsorDetail(
    val eventSponsorId: Long,
    val company: SponsorCompany,
    val packageLabel: String? = null,
    val packageColor: String? = null,
    val pipelineStatus: String = "",
    val dealType: String = "",
    val contractValueNet: Double? = null,
    val opsStatus: String = "",
    val tags: List<String> = emptyList(),
    val contacts: List<ContactPerson> = emptyList(),
    val benefits: List<SponsorBenefit> = emptyList()
)

data class SponsorCompany(
    val id: Long,
    val name: String,
    val nameShort: String,
    val nip: String = "",
    val industryCategory: String = "",
    val website: String = "",
    val emailGeneral: String = "",
    val phoneGeneral: String = "",
    val logoUrl: String? = null,
    val addressCity: String = "",
    val addressStreet: String = "",
    val addressPostalCode: String = "",
    val cooperationStatus: String = ""
)

data class ContactPerson(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String = "",
    val phone: String = "",
    val position: String = "",
    val department: String = ""
) {
    val displayName: String get() = "$firstName $lastName".trim()
}

data class SponsorBenefit(
    val name: String,
    val status: String = "",
    val category: String = ""
)
