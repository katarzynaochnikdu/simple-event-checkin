plugins {
    alias(libs.plugins.md.android.feature)
}

android {
    namespace = "pl.medidesk.mobile.feature.inhub"
}

dependencies {
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":core:core-sync"))

    // CameraX + MLKit for InHub QR scan
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.accompanist.permissions)
}
