package pl.medidesk.mobile.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "ticket_classes",
    primaryKeys = ["ticket_class_id", "event_id"]
)
data class TicketClassEntity(
    @ColumnInfo(name = "ticket_class_id") val ticketClassId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "ticket_name") val ticketName: String
)
