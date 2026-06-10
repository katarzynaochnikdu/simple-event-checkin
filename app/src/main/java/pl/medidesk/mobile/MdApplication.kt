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

            // Opt-out by default; AppNavHost / AnalyticsConsentDialog will optIn() if user accepted.
            // WO-MOB-031 / F2B-007 (kolejność consent): optOut MUSI być ustawiony tutaj,
            // w configu PRZED PostHogAndroid.setup() — SDK rodzi się wtedy opted-out i żaden
            // autocapture (lifecycle/screen view) nie wyprzedzi zgody. NIE zastępować
            // wywołaniem PostHog.optOut() PO setup() — to otwiera okno capture bez zgody.
            optOut = hasConsent != true

            // Autocapture
            captureApplicationLifecycleEvents = true
            // WO-MOB-031 / F2B-001: deep link niesie token resetu hasła
            // (medidesk://reset-password?token=...). Z flagą włączoną (default SDK) leci
            // event "Deep Link Opened" z KAŻDYM query paramem (w tym token=) + pełnym URL
            // do PostHog Cloud — credential ląduje bezterminowo w 3rd party storze.
            // NIGDY nie włączać. Deep-link handling aplikacji (MainActivity._deepLink +
            // AppNavHost) jest od tej flagi w pełni niezależny.
            captureDeepLinks = false
            captureScreenViews = true

            // Session replay — only when consented (sample rate set in PostHog dashboard, not SDK)
            sessionReplay = hasConsent == true
            // Hardening replay ustawiamy BEZWARUNKOWO (nie tylko przy consent==true):
            // sessionReplayConfig to zwykły data holder (wartości bez side-effectów przy
            // replay OFF), a flagi bezpieczeństwa nie mogą zależeć od stanu zgody w chwili
            // startu — zero fail-open, gdyby warunek włączania replay kiedyś się zmienił.
            sessionReplayConfig.apply {
                // WO-MOB-031 / F2B-002: SDK default = true → logcat procesu płynąłby do
                // PostHog jako console events. To niezadeklarowany kanał danych do 3rd party
                // (TELEMETRY.md §3.4 / Privacy Policy §4.4) + automatyczna eksfiltracja
                // każdej przyszłej regresji log-hygiene. Trzymać false.
                captureLogcat = false
                // Aggressive masking — operator app shows PII (imię, email, telefon, NIP)
                // na większości ekranów. Wartość Session Replay = workflow operatora
                // (kliknięcia, swipe'y, lagi) — content nie jest potrzebny.
                maskAllTextInputs = true  // form inputs (hasła, NIP)
                maskAllImages = true       // participant photos, logos with names
            }

            // WO-MOB-031 / F2B-009: redakcja nagłówka Authorization w network capture — n/a.
            // PostHogOkHttpInterceptor NIE jest dodany do klienta OkHttp (NetworkModule.kt
            // buduje go wyłącznie z AuthInterceptor + DEBUG-owy HttpLoggingInterceptor),
            // a sessionReplayConfig w SDK 3.11.0 nie eksponuje captureNetworkTelemetry —
            // network capture po stronie PostHog jest więc wyłączony i nie ma czego redagować.
            // Gdyby kiedyś dodawać PostHogOkHttpInterceptor: w 3.11.0 nie wysyła on nagłówków
            // (tylko url/method/status/timing), ale zweryfikować ponownie przy bumpie SDK
            // i odnotować w TELEMETRY.md.
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
