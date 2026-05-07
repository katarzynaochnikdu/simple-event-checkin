plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.android.library.compose)
}

android {
    namespace = "pl.medidesk.mobile.core.ui"
}

dependencies {
    implementation(project(":core:core-model"))
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
