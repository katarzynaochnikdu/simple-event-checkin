package pl.medidesk.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.medidesk.mobile.core.ui.theme.MdTheme
import pl.medidesk.mobile.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /**
     * Strumień deep-linków obsługiwanych przez aplikację.
     * Wystawiany do AppNavHost, który reaguje LaunchedEffect-em.
     * Obsługuje zarówno start aplikacji (cold) jak i `onNewIntent` (warm).
     */
    private val _deepLink = MutableStateFlow<Uri?>(null)
    val deepLink: StateFlow<Uri?> = _deepLink.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cold-start deep link
        captureDeepLink(intent)

        setContent {
            val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
            val darkTheme = when (themePref) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            val pendingDeepLink by deepLink.collectAsStateWithLifecycle()
            MdTheme(darkTheme = darkTheme) {
                AppNavHost(
                    pendingDeepLink = pendingDeepLink,
                    onDeepLinkConsumed = { _deepLink.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm deep link (apka już żyje)
        setIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "medidesk") {
            _deepLink.value = data
        }
    }
}
