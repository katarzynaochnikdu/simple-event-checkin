package pl.medidesk.mobile.feature.auth.domain.usecase

import pl.medidesk.mobile.core.model.User
import pl.medidesk.mobile.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank()) return Result.failure(Exception("E-mail jest wymagany"))
        if (password.isBlank()) return Result.failure(Exception("Hasło jest wymagane"))
        return authRepository.login(email.trim(), password)
    }
}
