plugins {
    alias(libs.plugins.md.android.feature)
}

android {
    namespace = "pl.medidesk.mobile.feature.addorder"
}

dependencies {
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":core:core-sync"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-ui"))
}
