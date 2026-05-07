package pl.medidesk.mobile.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "last_participants_sync") val lastParticipantsSync: String? = null,
    @ColumnInfo(name = "last_checkin_push") val lastCheckinPush: String? = null,
    @ColumnInfo(name = "last_walkin_push") val lastWalkinPush: String? = null
)
