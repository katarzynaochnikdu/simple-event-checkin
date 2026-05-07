package pl.medidesk.mobile.feature.auth.domain.repository

import pl.medidesk.mobile.core.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean
    suspend fun getCurrentUser(): User?
}
