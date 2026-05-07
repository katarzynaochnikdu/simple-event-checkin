package pl.medidesk.mobile.feature.events.domain.repository

import pl.medidesk.mobile.core.model.EventItem

interface EventsRepository {
    suspend fun getEvents(): Result<List<EventItem>>
}
