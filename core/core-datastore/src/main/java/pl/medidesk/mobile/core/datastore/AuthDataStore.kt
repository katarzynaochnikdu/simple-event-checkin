package pl.medidesk.mobile.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// DataStore only for non-sensitive UI preferences (theme, analytics consent).
// Keeps the same "auth_prefs" filename so existing theme/analytics values survive the upgrade.
private val Context.uiPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

// Keys for EncryptedSharedPreferences (auth secrets)
private const val ENCRYPTED_PREFS_FILE = "auth_secure_prefs"
private const val KEY_TOKEN            = "jwt_token"
private const val KEY_USER_ID          = "user_id"
private const val KEY_USER_EMAIL       = "user_email"
private const val KEY_USER_DISPLAY     = "user_display_name"
private const val KEY_USER_FIRST       = "user_first_name"
private const val KEY_USER_LAST        = "user_last_name"
private const val KEY_USER_ROLE        = "user_role"

/**
 * Auth & UI preferences store.
 *
 * Security split (WO-201):
 *  • Sensitive auth secrets (JWT token, email, name, role) → EncryptedSharedPreferences
 *    backed by Android Keystore (AES256-GCM). Inaccessible without device unlock.
 *  • Non-sensitive UI prefs (theme, analytics consent) → plain DataStore<Preferences>
 *    in the original "auth_prefs" file (transparent upgrade — no data loss).
 *
 * Public API is intentionally identical to the previous plaintext implementation
 * so no callers need to change.
 */
@Singleton
class AuthDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── EncryptedSharedPreferences — created once on first access ──────────
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── MutableStateFlows backed by EncryptedSharedPreferences ────────────
    // Initialised from disk in the init block; updated on every write.
    private val _tokenFlow        = MutableStateFlow<String?>(null)
    private val _userIdFlow       = MutableStateFlow<String?>(null)
    private val _userEmailFlow    = MutableStateFlow<String?>(null)
    private val _userFirstFlow    = MutableStateFlow<String?>(null)
    private val _userLastFlow     = MutableStateFlow<String?>(null)
    private val _userRoleFlow     = MutableStateFlow<String?>(null)

    val tokenFlow:        Flow<String?> = _tokenFlow.asStateFlow()
    val userIdFlow:       Flow<String?> = _userIdFlow.asStateFlow()
    val userEmailFlow:    Flow<String?> = _userEmailFlow.asStateFlow()
    val userFirstNameFlow:Flow<String?> = _userFirstFlow.asStateFlow()
    val userLastNameFlow: Flow<String?> = _userLastFlow.asStateFlow()
    val userRoleFlow:     Flow<String?> = _userRoleFlow.asStateFlow()

    init {
        // EncryptedSharedPreferences reads are fast (in-memory cache after first open).
        // Hilt creates this singleton during DI graph resolution — acceptable on any thread
        // for a single synchronous read.
        _tokenFlow.value     = encryptedPrefs.getString(KEY_TOKEN,      null)
        _userIdFlow.value    = encryptedPrefs.getString(KEY_USER_ID,     null)
        _userEmailFlow.value = encryptedPrefs.getString(KEY_USER_EMAIL,  null)
        _userFirstFlow.value = encryptedPrefs.getString(KEY_USER_FIRST,  null)
        _userLastFlow.value  = encryptedPrefs.getString(KEY_USER_LAST,   null)
        _userRoleFlow.value  = encryptedPrefs.getString(KEY_USER_ROLE,   null)
    }

    // ── Auth write helpers ─────────────────────────────────────────────────

    suspend fun saveToken(token: String) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(KEY_TOKEN, token).apply()
        }
        _tokenFlow.value = token
    }

    suspend fun saveUserInfo(
        id: Int,
        email: String,
        firstName: String,
        lastName: String,
        role: String
    ) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit()
                .putString(KEY_USER_ID,      id.toString())
                .putString(KEY_USER_EMAIL,   email)
                .putString(KEY_USER_DISPLAY, "$firstName $lastName")
                .putString(KEY_USER_FIRST,   firstName)
                .putString(KEY_USER_LAST,    lastName)
                .putString(KEY_USER_ROLE,    role)
                .apply()
        }
        _userIdFlow.value    = id.toString()
        _userEmailFlow.value = email
        _userFirstFlow.value = firstName
        _userLastFlow.value  = lastName
        _userRoleFlow.value  = role
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().clear().apply()
        }
        _tokenFlow.value     = null
        _userIdFlow.value    = null
        _userEmailFlow.value = null
        _userFirstFlow.value = null
        _userLastFlow.value  = null
        _userRoleFlow.value  = null
        // Also clear UI prefs (theme resets to SYSTEM, analytics dialog will show again)
        context.uiPrefsDataStore.edit { it.clear() }
    }

    /** Quick access without collecting the flow. */
    fun getToken(): String? = _tokenFlow.value

    // ── UI prefs ───────────────────────────────────────────────────────────

    private val themeKey         = stringPreferencesKey("theme_preference")
    private val analyticsKey     = stringPreferencesKey("analytics_consent")

    val themePreferenceFlow: Flow<String> =
        context.uiPrefsDataStore.data.map { it[themeKey] ?: "SYSTEM" }

    /**
     * null  = never set (show consent dialog)
     * true  = accepted analytics
     * false = declined analytics
     */
    val analyticsConsentFlow: Flow<Boolean?> =
        context.uiPrefsDataStore.data.map { prefs ->
            prefs[analyticsKey]?.let { it == "true" }
        }

    suspend fun saveThemePreference(theme: String) {
        context.uiPrefsDataStore.edit { it[themeKey] = theme }
    }

    suspend fun saveAnalyticsConsent(consent: Boolean) {
        context.uiPrefsDataStore.edit { it[analyticsKey] = consent.toString() }
    }
}
