package pl.medidesk.mobile.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import pl.medidesk.mobile.core.database.dao.*
import pl.medidesk.mobile.core.database.entities.*

@Database(
    entities = [
        ParticipantEntity::class,
        OfflineCheckinEntity::class,
        SyncMetadataEntity::class,
        WalkinEntity::class,
        TicketClassEntity::class
    ],
    version = 9, // v9: added rsvp_sent / rsvp_response / rsvp_responded_at columns to participants (WO-MOB-003)
    exportSchema = true
)
abstract class MdDatabase : RoomDatabase() {
    abstract fun participantDao(): ParticipantDao
    abstract fun offlineCheckinDao(): OfflineCheckinDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun walkinDao(): WalkinDao
    abstract fun ticketClassDao(): TicketClassDao
}
