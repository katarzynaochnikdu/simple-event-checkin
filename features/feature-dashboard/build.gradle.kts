plugins {
    alias(libs.plugins.md.android.feature)
}

android {
    namespace = "pl.medidesk.mobile.feature.dashboard"
}

dependencies {
    implementation(project(":core:core-sync"))
    implementation(project(":features:feature-events"))
    implementation(project(":features:feature-auth"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":features:feature-add-order"))
}
