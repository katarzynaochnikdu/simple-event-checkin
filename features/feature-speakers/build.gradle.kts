plugins {
    alias(libs.plugins.md.android.feature)
}

android {
    namespace = "pl.medidesk.mobile.feature.speakers"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-datastore"))
    // WO-MOB-015: speaker check-in needs SyncEngine + offline queue path
    implementation(project(":core:core-sync"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
}
