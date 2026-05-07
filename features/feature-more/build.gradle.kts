plugins {
    alias(libs.plugins.md.android.feature)
}

android {
    namespace = "pl.medidesk.mobile.feature.more"
}

dependencies {
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":core:core-sync"))
}
