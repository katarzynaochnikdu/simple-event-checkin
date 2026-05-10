package pl.medidesk.mobile.core.model

data class User(
    val id: Int,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val mustChangePassword: Boolean = false
) {
    val displayName: String get() = "$firstName $lastName"
}
