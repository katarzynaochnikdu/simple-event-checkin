plugins {
    alias(libs.plugins.md.android.feature)
}

android {
    namespace = "pl.medidesk.mobile.feature.auth"
}

dependencies {
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":core:core-analytics"))
    // WO-MOB-016: ParticipantTagsRepository.refresh() po loginie
    implementation(project(":core:core-sync"))
}
