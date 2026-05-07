plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)   // applies ksp + hilt.android
    alias(libs.plugins.room)
}

android {
    namespace = "pl.medidesk.mobile.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:core-model"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}
