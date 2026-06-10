plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.android.library.compose)
}

android {
    namespace = "pl.medidesk.mobile.core.ui"
    // WO-MOB-034 (N-3): SecureDialogEffect gatuje FLAG_SECURE na `!BuildConfig.DEBUG`.
    // Plugin md.android.library nie generuje BuildConfig domyślnie — opt-in tutaj.
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-model"))
    // WO-MOB-016: ParticipantTagChip Composable konsumuje ParticipantTagDefinitionDto
    implementation(project(":core:core-network"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    api(libs.compose.ui.tooling.preview)
    api(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Image loading (Coil) — centralized here, transitive to all features
    api(libs.coil.compose)
    api(libs.coil.svg)
    api(libs.coil.gif)
}
