plugins {
    alias(libs.plugins.md.android.feature)
}

android {
    namespace = "pl.medidesk.mobile.feature.participants"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":core:core-sync"))
    implementation(project(":core:core-mappers"))
    implementation(project(":features:feature-add-order"))
}
