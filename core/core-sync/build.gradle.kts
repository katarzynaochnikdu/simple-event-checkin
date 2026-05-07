plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)
}

android {
    namespace = "pl.medidesk.mobile.core.sync"
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-datastore"))

    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
