plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)   // applies ksp + hilt.android
}

android {
    namespace = "pl.medidesk.mobile.core.network"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-datastore"))

    api(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.kotlinx.coroutines.android)
}
