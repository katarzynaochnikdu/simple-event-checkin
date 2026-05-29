plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)
}

android {
    namespace = "pl.medidesk.mobile.core.testing"
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))

    // Test utilities are exposed via `api` so that a consuming module's test source set
    // (testImplementation(project(":core:core-testing"))) gets them transitively — WO-MOB-024.
    api(libs.junit)
    api(libs.mockk)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    api(libs.okhttp.mockwebserver)
    api(libs.hilt.android.testing)

    // Needed by the skeleton dispatcher/scope helpers in this MAIN source set.
    implementation(libs.kotlinx.coroutines.android)
}
