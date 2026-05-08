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
    version = 7, // v7: added payment_method, purchaser_nip, purchaser_company, order_participants_total/checked_in to participants
    exportSchema = true
)
abstract class MdDatabase : RoomDatabase() {
    abstract fun participantDao(): ParticipantDao
    abstract fun offlineCheckinDao(): OfflineCheckinDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun walkinDao(): WalkinDao
    abstract fun ticketClassDao(): TicketClassDao
}
