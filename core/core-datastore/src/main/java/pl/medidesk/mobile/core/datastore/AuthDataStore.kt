package pl.medidesk.mobile.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tokenKey = stringPreferencesKey("jwt_token")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val userDisplayNameKey = stringPreferencesKey("user_display_name")
    private val userFirstNameKey = stringPreferencesKey("user_first_name")
    private val userLastNameKey = stringPreferencesKey("user_last_name")
    private val userRoleKey = stringPreferencesKey("user_role")
    private val userIdKey = stringPreferencesKey("user_id")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[tokenKey] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[userIdKey] }
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[userEmailKey] }
    val userFirstNameFlow: Flow<String?> = context.dataStore.data.map { it[userFirstNameKey] }
    val userLastNameFlow: Flow<String?> = context.dataStore.data.map { it[userLastNameKey] }
    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[userRoleKey] }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[tokenKey] = token }
    }

    suspend fun saveUserInfo(id: Int, email: String, firstName: String, lastName: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[userIdKey] = id.toString()
            prefs[userEmailKey] = email
            prefs[userDisplayNameKey] = "$firstName $lastName"
            prefs[userFirstNameKey] = firstName
            prefs[userLastNameKey] = lastName
            prefs[userRoleKey] = role
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getToken(): String? = tokenFlow.firstOrNull()
}
