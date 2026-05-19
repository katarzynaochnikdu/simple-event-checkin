plugins {
    alias(libs.plugins.md.android.library)
}

android {
    namespace = "pl.medidesk.mobile.core.mappers"
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-database"))

    implementation(libs.kotlinx.coroutines.android)
}
