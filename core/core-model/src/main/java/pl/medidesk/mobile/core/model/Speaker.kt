package pl.medidesk.mobile.core.model

data class Speaker(
    val speakerId: String,
    val firstName: String,
    val lastName: String,
    val title: String = "",
    val affiliation: String = "",
    val organization: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val bioLong: String = "",
    val email: String = "",
    val phone: String = "",
    val socialLinkedin: String = "",
    val socialTwitter: String = "",
    val website: String = "",
    val academicTitle: String = ""
) {
    val displayName: String get() = buildString {
        if (title.isNotBlank()) append("$title ")
        append("$firstName $lastName")
    }.trim()

    val initials: String get() = "${firstName.take(1)}${lastName.take(1)}".uppercase()
}
