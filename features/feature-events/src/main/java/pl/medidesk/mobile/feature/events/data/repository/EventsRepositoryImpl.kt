package pl.medidesk.mobile.feature.events.data.repository

import pl.medidesk.mobile.core.model.EventItem
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.feature.events.domain.repository.EventsRepository
import javax.inject.Inject

class EventsRepositoryImpl @Inject constructor(
    private val apiService: MobileApiService
) : EventsRepository {

    private var cachedEvents: List<EventItem>? = null
    private var cacheTimestamp: Long = 0L
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minut

    override suspend fun getEvents(): Result<List<EventItem>> {
        val now = System.currentTimeMillis()
        cachedEvents?.takeIf { now - cacheTimestamp < CACHE_TTL_MS }?.let {
            return Result.success(it)
        }
        return fetchFromNetwork()
    }

    private suspend fun fetchFromNetwork(): Result<List<EventItem>> = try {
        val response = apiService.getEvents()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            val events = body.events.map { dto ->
                EventItem(
                    eventId = dto.eventId ?: dto.id ?: "",
                    eventName = dto.eventName ?: dto.name ?: dto.title ?: "Wydarzenie",
                    status = dto.status ?: "active",
                    startDate = dto.startDate ?: dto.startsAt ?: dto.startAt ?: dto.date ?: dto.start ?: dto.startTime ?: dto.eventDate ?: "",
                    endDate = dto.endDate ?: "",
                    venue = dto.venue ?: dto.location ?: dto.address ?: "",
                    imageUrl = dto.imageUrl ?: dto.image ?: dto.thumbnail,
                    logoUrl = dto.logoUrl,
                    logoColorUrl = dto.logoColorUrl,
                    logoWhiteUrl = dto.logoWhiteUrl,
                    primaryColor = dto.primaryColor,
                    secondaryColor = dto.secondaryColor,
                    accentColor = dto.accentColor
                )
            }
            cachedEvents = events
            cacheTimestamp = System.currentTimeMillis()
            Result.success(events)
        } else {
            Result.failure(Exception("Błąd pobierania eventów"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
