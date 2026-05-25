package pl.medidesk.mobile.core.model

data class DashboardData(
    val eventId: String,
    val totalRegistered: Int,
    val totalWithQr: Int,
    val checkedIn: Int,
    val walkIns: Int,
    val checkInRate: Double,
    val byTicketClass: List<TicketClassStat> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val topScanners: List<ScannerStat> = emptyList(),
    val recentCheckins: List<Participant> = emptyList(),
    val eventName: String = "",
    val startDate: String = "",
    val venue: String = "",
    val imageUrl: String? = null, // Dodane dla nagłówka Dashboardu
    val logoUrl: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val accentColor: String? = null,
    // WO-MOB-015 (2026-05-25): speakers attendance stats
    val speakersTotal: Int = 0,
    val speakersAttended: Int = 0
)

data class TicketClassStat(
    val ticketName: String,
    val total: Int,
    val checkedIn: Int
)

data class TimelineEntry(
    val hour: String,
    val count: Int
)

data class ScannerStat(
    val email: String,
    val count: Int
)

data class CheckinStats(
    val eventId: String,
    val totalWithQr: Int,
    val checkedIn: Int,
    val notCheckedIn: Int,
    val scanners: List<ScannerStat>
)
