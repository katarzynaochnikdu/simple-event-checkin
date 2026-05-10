package pl.medidesk.mobile.core.model

data class EventItem(
    val eventId: String,
    val eventName: String,
    val status: String,
    val startDate: String,
    val endDate: String,
    val venue: String,
    val imageUrl: String? = null,
    val logoUrl: String? = null,
    val logoColorUrl: String? = null,
    val logoWhiteUrl: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val accentColor: String? = null
)
