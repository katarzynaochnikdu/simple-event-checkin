package pl.medidesk.mobile.feature.speakers.data.repository

import android.util.Log
import pl.medidesk.mobile.core.database.dao.SpeakerCheckinDao
import pl.medidesk.mobile.core.database.entities.SpeakerCheckinEntity
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.SpeakerCheckinRequestDto
import pl.medidesk.mobile.core.network.dto.SpeakerCheckinResponseDto
import pl.medidesk.mobile.core.network.dto.SpeakerCheckinStatsDto
import pl.medidesk.mobile.core.sync.SyncEngine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repozytorium check-in prelegentow (WO-MOB-015, 2026-05-25).
 *
 * Strategia online-first z offline fallback (analogia do CheckinUseCase dla uczestnikow):
 *   1. Probuje wyslac do backendu /api/mobile/events/.../speakers/.../checkin
 *   2. On success: zapisuje row do lokalnej Room (synced=true) — local cache do
 *      rebuild'u UI po restart aplikacji bez ponownego /checkin-stats.
 *   3. On network/HTTP failure: zapisuje row do queue (synced=false), SyncWorker
 *      pushnie batch przy nastepnej okazji.
 *
 * Action semantyka:
 *   - "check-in" — odhaczenie obecnosci
 *   - "check-out" — undo (cofniecie odhaczenia)
 *
 * Backend dedupuje po (event_id, speaker_id, last action) — idempotent na zlecenie.
 */
@Singleton
class SpeakerCheckinRepository @Inject constructor(
    private val apiService: MobileApiService,
    private val speakerCheckinDao: SpeakerCheckinDao,
    private val syncEngine: SyncEngine
) {

    companion object {
        private const val TAG = "SpeakerCheckinRepo"
    }

    sealed class Result {
        data class Success(
            val attendedAt: String?,
            val action: String,
            val isOffline: Boolean = false,
            val isDuplicate: Boolean = false
        ) : Result()

        data class Failure(val message: String) : Result()
        data object NotFound : Result()
    }

    suspend fun markAttended(eventId: String, speakerId: String): Result =
        performCheckin(eventId, speakerId, action = "check-in")

    suspend fun undoAttended(eventId: String, speakerId: String): Result =
        performCheckin(eventId, speakerId, action = "check-out")

    suspend fun getStats(eventId: String): kotlin.Result<SpeakerCheckinStatsDto> {
        return try {
            val response = apiService.speakerCheckinStats(eventId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) kotlin.Result.success(body)
                else kotlin.Result.failure(Exception("Empty response"))
            } else {
                kotlin.Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    private suspend fun performCheckin(eventId: String, speakerId: String, action: String): Result {
        val scannedAt = Instant.now().toString()
        return try {
            val response = apiService.speakerCheckin(
                eventId = eventId,
                speakerId = speakerId,
                body = SpeakerCheckinRequestDto(deviceId = "android", scannedAt = null, action = action)
            )
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    Result.Failure("Empty response")
                } else {
                    handleOnlineSuccess(body, eventId, speakerId, scannedAt, action)
                }
            } else if (response.code() == 404) {
                Result.NotFound
            } else {
                Log.w(TAG, "Online speaker $action failed (HTTP ${response.code()}) — falling back to offline queue")
                queueOffline(eventId, speakerId, scannedAt, action)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online speaker $action exception (${e.message}) — falling back to offline queue")
            queueOffline(eventId, speakerId, scannedAt, action)
        }
    }

    private suspend fun handleOnlineSuccess(
        body: SpeakerCheckinResponseDto,
        eventId: String,
        speakerId: String,
        scannedAt: String,
        action: String
    ): Result {
        when (body.status) {
            "ok" -> {
                // Persist locally as already-synced — survives app restart without
                // depending on /checkin-stats refresh roundtrip.
                speakerCheckinDao.insert(
                    SpeakerCheckinEntity(
                        speakerId = speakerId,
                        eventId = eventId,
                        scannedAt = body.attendedAt ?: scannedAt,
                        deviceId = "android",
                        action = body.action ?: action,
                        synced = true
                    )
                )
                return Result.Success(
                    attendedAt = body.attendedAt,
                    action = body.action ?: action,
                    isOffline = false,
                    isDuplicate = false
                )
            }
            "already_checked_in" -> {
                return Result.Success(
                    attendedAt = body.attendedAt,
                    action = "check-in",
                    isOffline = false,
                    isDuplicate = true
                )
            }
            "not_checked_in" -> {
                // Trying undo when nothing to undo — treat as soft failure (UX already shows checked state).
                return Result.Failure(body.error ?: "Brak aktywnego check-in do cofniecia")
            }
            "not_found" -> return Result.NotFound
            else -> return Result.Failure(body.error ?: "Nieznany status: ${body.status}")
        }
    }

    private suspend fun queueOffline(
        eventId: String,
        speakerId: String,
        scannedAt: String,
        action: String
    ): Result {
        speakerCheckinDao.insert(
            SpeakerCheckinEntity(
                speakerId = speakerId,
                eventId = eventId,
                scannedAt = scannedAt,
                deviceId = "android",
                action = action,
                synced = false
            )
        )
        // Trigger immediate WorkManager sync — fires when network returns.
        syncEngine.triggerImmediateSync(eventId)
        return Result.Success(
            attendedAt = scannedAt,
            action = action,
            isOffline = true,
            isDuplicate = false
        )
    }
}
