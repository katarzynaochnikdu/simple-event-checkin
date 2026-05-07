package pl.medidesk.mobile.core.model

data class InHubConfig(
    val exists: Boolean,
    val id: Int? = null,
    val eventId: String? = null,
    val autoCheckin: Boolean = true,
    val showSearch: Boolean = true,
    val showWalkin: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
