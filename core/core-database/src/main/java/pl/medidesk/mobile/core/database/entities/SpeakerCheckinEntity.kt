package pl.medidesk.mobile.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline queue dla manualnego check-in prelegentow (WO-MOB-015, 2026-05-25).
 *
 * Strategia analogiczna do `OfflineCheckinEntity` (uczestnicy) — kazdy
 * tap (lub undo) najpierw zapisywany lokalnie z synced=false; SyncWorker
 * push'uje batch do backend /api/mobile/speakers/checkin/sync gdy network.
 *
 * Identyfikacja prelegenta = `speakerId: String` (= `event_speakers.speaker_id`,
 * globalny TEXT identyfikator). Backend mapuje text -> bigint event_speakers.id.
 *
 * Action: "check-in" (default) lub "check-out" (undo). Backend dedupuje po
 * (event_id, speaker_id, last action) — patrz `checkin_speaker()`.
 */
@Entity(
    tableName = "speaker_checkin_queue",
    indices = [
        Index("synced"),
        Index("event_id"),
        Index("speaker_id"),
    ]
)
data class SpeakerCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "speaker_id") val speakerId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "scanned_at") val scannedAt: String,
    @ColumnInfo(name = "device_id") val deviceId: String = "android",
    val action: String = "check-in",
    val synced: Boolean = false,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: String? = null
)
