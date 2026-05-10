package pl.medidesk.mobile.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "participants",
    indices = [
        Index("ticket_id"),
        Index("ticket_number"),
        Index("backstage_ticket_id"),
        Index("event_id")
    ]
)
data class ParticipantEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "ticket_id") val ticketId: String?,
    @ColumnInfo(name = "ticket_number") val ticketNumber: String? = null,
    @ColumnInfo(name = "backstage_ticket_id") val backstageTicketId: String?,
    @ColumnInfo(name = "first_name") val firstName: String?,
    @ColumnInfo(name = "last_name") val lastName: String?,
    val email: String?,
    val phone: String?,
    val company: String?,
    @ColumnInfo(name = "ticket_class_id") val ticketClassId: String?,
    @ColumnInfo(name = "ticket_name") val ticketName: String?,
    val status: String?,
    @ColumnInfo(name = "attendance_status") val attendanceStatus: String?,
    @ColumnInfo(name = "event_order_id") val eventOrderId: String?,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "checked_in_at") val checkedInAt: String?,
    @ColumnInfo(name = "order_status") val orderStatus: String? = null,
    @ColumnInfo(name = "is_walkin") val isWalkin: Boolean = false,
    val tags: String? = null, // Comma separated
    @ColumnInfo(name = "buyer_name") val buyerName: String? = null,
    @ColumnInfo(name = "buyer_email") val buyerEmail: String? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String? = null,
    @ColumnInfo(name = "purchaser_nip") val purchaserNip: String? = null,
    @ColumnInfo(name = "purchaser_company") val purchaserCompany: String? = null,
    @ColumnInfo(name = "order_participants_total") val orderParticipantsTotal: Int? = null,
    @ColumnInfo(name = "order_participants_checked_in") val orderParticipantsCheckedIn: Int? = null
)
