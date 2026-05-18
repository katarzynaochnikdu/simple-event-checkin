package pl.medidesk.mobile

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import android.os.Build
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import pl.medidesk.mobile.core.analytics.Analytics
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import pl.medidesk.mobile.core.datastore.AuthDataStore
import javax.inject.Inject

@HiltAndroidApp
class MdApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var authDataStore: AuthDataStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate() // Hilt injection happens here
        setupPostHog()
    }

    private fun setupPostHog() {
        if (BuildConfig.POSTHOG_API_KEY.isBlank()) {
            // No key configured (dev without local.properties entry) — skip silently
            return
        }

        // Read stored consent synchronously — DataStore file is tiny (<100 ms)
        // Safe: called once in Application.onCreate, not on main thread critical path
        val hasConsent = runBlocking {
            withTimeoutOrNull(300L) { authDataStore.analyticsConsentFlow.firstOrNull() }
        }

        // host is val in PostHogConfig — must be passed via constructor (not apply{})
        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST
        ).apply {
            debug = BuildConfig.DEBUG

            // Opt-out by default; AppNavHost / AnalyticsConsentDialog will optIn() if user accepted
            optOut = hasConsent != true

            // Autocapture
            captureApplicationLifecycleEvents = true
            captureDeepLinks = true
            captureScreenViews = true

            // Session replay — only when consented (sample rate set in PostHog dashboard, not SDK)
            sessionReplay = hasConsent == true
            if (hasConsent == true) {
                sessionReplayConfig.apply {
                    // Aggressive masking — operator app shows PII (imię, email, telefon, NIP)
                    // na większości ekranów. Wartość Session Replay = workflow operatora
                    // (kliknięcia, swipe'y, lagi) — content nie jest potrzebny.
                    maskAllTextInputs = true  // form inputs (hasła, NIP)
                    maskAllImages = true       // participant photos, logos with names
                }
            }
        }

        PostHogAndroid.setup(this, config)

        // Super properties — dodawane automatycznie do KAŻDEGO eventu.
        // Bez tego app_version / device_model trzeba by dorzucać ręcznie w każdym capture.
        Analytics.register(
            mapOf(
                "app_version" to BuildConfig.VERSION_NAME,
                "version_code" to BuildConfig.VERSION_CODE,
                "device_manufacturer" to (Build.MANUFACTURER ?: "unknown"),
                "device_model" to (Build.MODEL ?: "unknown"),
                "android_sdk" to Build.VERSION.SDK_INT,
                "build_type" to if (BuildConfig.DEBUG) "debug" else "release"
            )
        )
    }
}
