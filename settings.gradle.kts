pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()}
}

rootProject.name = "MD_mobile_android"

include(":app")

// Core modules
include(":core:core-model")
include(":core:core-network")
include(":core:core-database")
include(":core:core-datastore")
include(":core:core-ui")
include(":core:core-sync")
include(":core:core-analytics")
include(":core:core-mappers")
// WO-MOB-024 (2026-05-28): test-infra foundation (TestDispatchers, MainDispatcherRule, MockWebServer dispatcher).
include(":core:core-testing")

// Feature modules (Limited for Simple App)
include(":features:feature-auth")
include(":features:feature-events")
include(":features:feature-scanner")
include(":features:feature-participants")
include(":features:feature-dashboard")
include(":features:feature-more")
include(":features:feature-add-order")
// WO-MOB-015 (2026-05-25): re-enabled feature-speakers for manual speaker check-in.
include(":features:feature-speakers")

// Modules hidden/removed for simplification — NIE shipują w APK (zero project()-deps).
//
// 🛑 WO-MOB-034 (F2A-012) — STRAŻNIK BEZPIECZEŃSTWA:
// Odkomentowanie któregokolwiek z poniższych modułów WYMAGA security review PRZED
// re-enable. Powód: każdy wnosi wrażliwą powierzchnię, dziś poza audytem MASVS:
//   - feature-inhub    → flow PIN-u (uwierzytelnianie wejścia do strefy)
//   - feature-sponsors → dane finansowe + NIP firm (PII osób trzecich)
//   - feature-walkin   → formularz PII gościa (UWAGA: data-path walk-in
//                        WalkinEntity/WalkinDao/SyncWorker SHIPUJE przez
//                        core-database/core-sync bez własnego UI — to OSOBNA sprawa,
//                        NIE ruszać; tu chodzi tylko o moduł UI).
// Re-enable bez review = regresja audytu Sprint 2.
// include(":features:feature-walkin")
// include(":features:feature-inhub")
// include(":features:feature-sponsors")
