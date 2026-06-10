package pl.medidesk.mobile.navigation

import android.net.Uri
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String, val arguments: List<NamedNavArgument> = emptyList()) {
    data object Login : Screen("login/{role}", listOf(
        navArgument("role") { type = NavType.StringType }
    )) {
        fun createRoute(role: String) = "login/$role"
    }

    data object Home : Screen("home")
    data object Events : Screen("events")
    
    data object Main : Screen("main/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "main/$eventId"
    }

    // Tabs inside Main - now all including eventId to be accessible by ViewModels
    data object Scanner : Screen("scanner/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "scanner/$eventId"
    }
    
    // Global Scanner (from Home)
    data object GlobalScanner : Screen("global_scanner")

    data object Participants : Screen("participants/{eventId}?filterType={filterType}&ticketClassId={ticketClassId}", listOf(
        navArgument("eventId") { type = NavType.StringType },
        navArgument("filterType") { type = NavType.StringType; nullable = true; defaultValue = null },
        navArgument("ticketClassId") { type = NavType.StringType; nullable = true; defaultValue = null }
    )) {
        fun createRoute(eventId: String, filterType: String? = null, ticketClassId: String? = null): String {
            val base = "participants/$eventId"
            val params = mutableListOf<String>()
            filterType?.let { params.add("filterType=$it") }
            ticketClassId?.let { params.add("ticketClassId=$it") }
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }

    data object Dashboard : Screen("dashboard/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "dashboard/$eventId"
    }

    data object Stats : Screen("stats/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "stats/$eventId"
    }

    data object InHub : Screen("inhub/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "inhub/$eventId"
    }

    data object More : Screen("more/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "more/$eventId"
    }
    
    data object Walkin : Screen("walkin/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "walkin/$eventId"
    }
    
    data object ParticipantDetails : Screen("participantDetails/{participantId}", listOf(
        navArgument("participantId") { type = NavType.LongType }
    )) {
        fun createRoute(participantId: Long) = "participantDetails/$participantId"
    }
    
    data object Speakers : Screen("speakers/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "speakers/$eventId"
    }

    data object SpeakerDetail : Screen("speaker_detail/{eventId}/{speakerId}", listOf(
        navArgument("eventId") { type = NavType.StringType },
        navArgument("speakerId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String, speakerId: String) = "speaker_detail/$eventId/$speakerId"
    }

    data object Sponsors : Screen("sponsors/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "sponsors/$eventId"
    }

    data object SponsorDetail : Screen("sponsor_detail/{eventId}/{eventSponsorId}", listOf(
        navArgument("eventId") { type = NavType.StringType },
        navArgument("eventSponsorId") { type = NavType.LongType }
    )) {
        fun createRoute(eventId: String, eventSponsorId: Long) = "sponsor_detail/$eventId/$eventSponsorId"
    }

    data object Companies : Screen("companies/{eventId}?role={role}", listOf(
        navArgument("eventId") { type = NavType.StringType },
        navArgument("role") { type = NavType.StringType; defaultValue = "participant" }
    )) {
        fun createRoute(eventId: String, role: String = "participant") = "companies/$eventId?role=$role"
    }

    data object Orders : Screen("orders/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "orders/$eventId"
    }

    data object MyMentees : Screen("my_mentees/{eventId}", listOf(
        navArgument("eventId") { type = NavType.StringType }
    )) {
        fun createRoute(eventId: String) = "my_mentees/$eventId"
    }

    data object Settings : Screen("settings")
    data object Attractions : Screen("attractions")

    /**
     * Reset hasła z deep linka `medidesk://reset-password?token=...`.
     * Token przekazywany jest dalej jako argument route'a.
     */
    data object ResetPassword : Screen("reset_password/{token}", listOf(
        navArgument("token") { type = NavType.StringType }
    )) {
        // WO-MOB-034 (F2A-010): Uri.encode(token) — surowy token w route bez enkodowania
        // mógł zawierać `/` `?` `#` (np. ze spreparowanego deep linka) → route nie matchuje
        // wzorca → IllegalArgumentException NavControllera → crash (lokalny DoS).
        // NavType.StringType URL-dekoduje argument przy odczycie, więc round-trip jest spójny.
        fun createRoute(token: String) = "reset_password/${Uri.encode(token)}"
    }
}
