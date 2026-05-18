package pl.medidesk.mobile.core.analytics

/**
 * Single source of truth for all custom event names and property keys.
 * Keep alphabetical within each group.
 */
object AnalyticsEvent {

    // --- Auth ---
    const val USER_LOGGED_IN = "user_logged_in"
    const val USER_LOGGED_OUT = "user_logged_out"

    // --- Consent ---
    const val ANALYTICS_CONSENT_CHANGED = "analytics_consent_changed"

    // --- Scanning (WO-161) ---
    const val QR_SCAN_COMPLETED = "qr_scan_completed"
    const val CHECKIN_UNDONE = "checkin_undone"

    // --- Sync (WO-161) ---
    const val SYNC_COMPLETED = "sync_completed"
    const val SYNC_FAILED = "sync_failed"

    // --- Events (WO-161) ---
    const val EVENT_OPENED = "event_opened"

    // --- Orders (WO-161) ---
    const val ADD_ORDER_STARTED = "add_order_started"
    const val ADD_ORDER_COMPLETED = "add_order_completed"

    object Props {
        const val ROLE = "role"
        const val ACTION = "action"
        const val APP_VERSION = "app_version"
        const val EVENT_ID = "event_id"
        const val RESULT = "result"
        const val IS_OFFLINE = "is_offline"
        const val PUSHED_COUNT = "pushed_count"
        const val PULLED_COUNT = "pulled_count"
        const val DURATION_MS = "duration_ms"
        const val FORCE_FULL = "force_full"
        const val ERROR_TYPE = "error_type"
        const val PENDING_COUNT = "pending_count"
        const val PAYMENT_METHOD = "payment_method"
    }
}
