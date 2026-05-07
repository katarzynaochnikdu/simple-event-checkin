package pl.medidesk.mobile.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_checkins",
    indices = [Index("synced")]
)
data class OfflineCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ticket_id") val ticketId: String? = null,
    @ColumnInfo(name = "backstage_ticket_id") val backstageTicketId: String? = null,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "scanned_at") val scannedAt: String,
    @ColumnInfo(name = "device_id") val deviceId: String = "android",
    val synced: Boolean = false,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: String? = null,
    val action: String = "checkin"
)
