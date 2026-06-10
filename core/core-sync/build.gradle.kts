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

    // WO-MOB-028: LogoutUseCase woła MdDatabase.clearAllTables() — metodę supertypu RoomDatabase.
    // core-database trzyma room-runtime jako `implementation`, więc typ RoomDatabase nie jest
    // widoczny transytywnie w compile classpath tego modułu; runtime'owo był tu od zawsze.
    implementation(libs.room.runtime)

    // WO-MOB-028: unit test LogoutUseCase (MockK verify clearAllTables + clearAll).
    // core-testing eksponuje junit/mockk/coroutines-test przez `api` (WO-MOB-024).
    testImplementation(project(":core:core-testing"))
}
