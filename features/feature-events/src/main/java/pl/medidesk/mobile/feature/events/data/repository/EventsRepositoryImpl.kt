package pl.medidesk.mobile.feature.events.data.repository

import pl.medidesk.mobile.core.model.EventItem
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.feature.events.domain.repository.EventsRepository
import javax.inject.Inject

class EventsRepositoryImpl @Inject constructor(
    private val apiService: MobileApiService
) : EventsRepository {
    override suspend fun getEvents(): Result<List<EventItem>> = try {
        val response = apiService.getEvents()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body.events.map { dto ->
                EventItem(
                    eventId = dto.eventId ?: dto.id ?: "",
                    eventName = dto.eventName ?: dto.name ?: dto.title ?: "Wydarzenie",
                    status = dto.status ?: "active",
                    startDate = dto.startDate ?: dto.startsAt ?: dto.startAt ?: dto.date ?: dto.start ?: dto.startTime ?: dto.eventDate ?: "",
                    endDate = dto.endDate ?: "",
                    venue = dto.venue ?: dto.location ?: dto.address ?: "",
                    imageUrl = dto.imageUrl ?: dto.image ?: dto.thumbnail
                )
            })
        } else {
            Result.failure(Exception("Błąd pobierania eventów"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
