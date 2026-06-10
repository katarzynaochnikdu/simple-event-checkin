package pl.medidesk.mobile.feature.auth.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.model.User
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.dto.LoginRequest
import pl.medidesk.mobile.core.sync.LogoutUseCase
import pl.medidesk.mobile.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val apiService: MobileApiService,
    private val authDataStore: AuthDataStore,
    private val logoutUseCase: LogoutUseCase
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            val body = response.body()
            if (response.isSuccessful && body?.success == true && body.token != null && body.user != null) {
                authDataStore.saveToken(body.token!!)
                val u = body.user!!
                authDataStore.saveUserInfo(u.id, u.email, u.firstName ?: "", u.lastName ?: "", u.role ?: "PARTICIPANT")
                Result.success(User(u.id, u.email, u.firstName ?: "", u.lastName ?: "", u.role ?: "PARTICIPANT", body.mustChangePassword))
            } else {
                Result.failure(Exception(body?.error ?: "Błąd logowania"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        // WO-MOB-028 (F2A-001): pełny wipe lokalnych danych (Room + encrypted prefs + reset
        // analytics) zamiast samego clearAll(). Bez best-effort flushu kolejek — kontrakt
        // repo nie rozróżnia kontekstu wywołania (flush robią jawnie Settings/More).
        logoutUseCase()
    }

    override suspend fun isLoggedIn(): Boolean =
        authDataStore.tokenFlow.firstOrNull() != null

    override suspend fun getCurrentUser(): User? = null
}
