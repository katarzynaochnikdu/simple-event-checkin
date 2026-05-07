package pl.medidesk.mobile.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "walkin_participants",
    indices = [Index("event_id"), Index(value = ["walk_in_code"], unique = true)]
)
data class WalkinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "walk_in_code") val walkInCode: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val company: String? = null,
    @ColumnInfo(name = "ticket_class_id") val ticketClassId: String? = null,
    @ColumnInfo(name = "ticket_name") val ticketName: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "checked_in_at") val checkedInAt: String? = null,
    val status: String = "registered",
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "pending"
)
