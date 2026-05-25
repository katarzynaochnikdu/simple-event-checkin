package pl.medidesk.mobile.core.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------------
// SPEAKER CHECK-IN — WO-MOB-015 (2026-05-25)
// Manualny check-in prelegentow (bez QR) z mobile.
// Backend endpoints:
//   POST /api/mobile/events/{eventId}/speakers/{speakerId}/checkin
//   POST /api/mobile/speakers/checkin/sync
//   GET  /api/mobile/events/{eventId}/speakers/checkin-stats
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class SpeakerCheckinRequestDto(
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "scanned_at") val scannedAt: String? = null,
    val action: String = "check-in"
)

@JsonClass(generateAdapter = true)
data class SpeakerCheckinResponseDto(
    val status: String,
    @Json(name = "attended_at") val attendedAt: String? = null,
    val action: String? = null,
    @Json(name = "log_id") val logId: Long? = null,
    @Json(name = "speaker_id") val speakerId: String? = null,
    @Json(name = "event_speakers_id") val eventSpeakersId: Long? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class SpeakerCheckinStatsDto(
    @Json(name = "event_id") val eventId: String,
    val total: Int,
    val attended: Int,
    @Json(name = "attended_speaker_ids") val attendedSpeakerIds: List<String>
)

@JsonClass(generateAdapter = true)
data class SpeakerCheckinBatchEntryDto(
    @Json(name = "event_id") val eventId: String,
    @Json(name = "speaker_id") val speakerId: String,
    @Json(name = "scanned_at") val scannedAt: String,
    @Json(name = "device_id") val deviceId: String? = null,
    val action: String = "check-in"
)

@JsonClass(generateAdapter = true)
data class SpeakerCheckinSyncBatchDto(
    val entries: List<SpeakerCheckinBatchEntryDto>
)

@JsonClass(generateAdapter = true)
data class SpeakerCheckinSyncResultDto(
    val total: Int = 0,
    val synced: Int = 0,
    val duplicates: Int = 0,
    @Json(name = "not_found") val notFound: Int = 0,
    val errors: Int = 0,
    val results: List<SpeakerCheckinResponseDto> = emptyList()
)
