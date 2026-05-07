package pl.medidesk.mobile.core.model

data class EventItem(
    val eventId: String,
    val eventName: String,
    val status: String,
    val startDate: String,
    val endDate: String,
    val venue: String,
    val imageUrl: String? = null
)
