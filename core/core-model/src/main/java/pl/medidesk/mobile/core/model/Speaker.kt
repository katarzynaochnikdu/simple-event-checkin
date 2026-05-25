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
    // WO-MOB-015 hotfix3 (2026-05-25): pomijaj honorific "Pan"/"Pani" (es.title) w displayName.
    // Zachowaj academicTitle (Dr/Prof./mgr) bo to faktyczna kwalifikacja prelegenta.
    val displayName: String get() = buildString {
        if (academicTitle.isNotBlank()) append("$academicTitle ")
        append("$firstName $lastName")
    }.trim()

    val initials: String get() = "${firstName.take(1)}${lastName.take(1)}".uppercase()
}
