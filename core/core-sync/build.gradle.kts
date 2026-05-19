plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)
}

android {
    namespace = "pl.medidesk.mobile.core.sync"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-analytics"))
    implementation(project(":core:core-mappers"))

    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
