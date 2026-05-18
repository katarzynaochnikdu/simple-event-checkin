package pl.medidesk.mobile.core.analytics

import com.posthog.PostHog

/**
 * Thin static facade over PostHog SDK.
 * All feature modules call Analytics.* — they never import PostHog directly.
 * This allows swapping providers without touching feature code.
 */
object Analytics {

    fun capture(event: String, properties: Map<String, Any>? = null) {
        PostHog.capture(event, properties = properties)
    }

    fun identify(userId: String, role: String? = null) {
        val userProperties = mutableMapOf<String, Any>("user_id" to userId)
        role?.let { userProperties["role"] = it }
        PostHog.identify(userId, userProperties = userProperties)
    }

    fun reset() {
        PostHog.reset()
    }

    fun optIn() {
        PostHog.optIn()
    }

    fun optOut() {
        PostHog.optOut()
    }

    fun isFeatureEnabled(key: String): Boolean {
        return PostHog.isFeatureEnabled(key)
    }

    fun getFeatureFlagPayload(key: String): Any? {
        return PostHog.getFeatureFlagPayload(key)
    }

    /**
     * Register super properties — added automatically to every subsequent event.
     * Use for stable values like app_version, device_model. Call once after setup.
     * PostHog SDK v3 expects per-key registration, so we iterate the map here.
     */
    fun register(properties: Map<String, Any>) {
        properties.forEach { (key, value) -> PostHog.register(key, value) }
    }

    /**
     * Force-refresh feature flags from server. Call after identify() so the user
     * gets flags scoped to their user_id (not just anonymous bucket).
     */
    fun reloadFeatureFlags() {
        PostHog.reloadFeatureFlags()
    }
}
