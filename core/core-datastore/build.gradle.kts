plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)
}

android {
    namespace = "pl.medidesk.mobile.core.datastore"
}

dependencies {
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
}
