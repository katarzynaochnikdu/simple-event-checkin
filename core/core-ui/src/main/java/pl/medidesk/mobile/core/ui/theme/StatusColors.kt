package pl.medidesk.mobile.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for order/payment status colors.
 * Used by ParticipantsScreen, OrdersScreen and any future status-pill UI.
 * Keeps visual language consistent across screens in both light & dark mode.
 */
object StatusColors {
    val Paid: Color = MdGreen           // 0xFF2E7D32 — opłacone, bezpłatne
    val Pending: Color = MdAmber        // 0xFFF57C00 — oczekuje na płatność / received
    val Cancelled: Color = MdRed        // 0xFFC62828 — anulowane / failed / unpaid
    val Neutral: Color = MdGrey700      // 0xFF616161 — wygasłe, zwrot
}
