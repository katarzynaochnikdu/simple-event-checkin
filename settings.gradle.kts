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

// Feature modules (Limited for Simple App)
include(":features:feature-auth")
include(":features:feature-events")
include(":features:feature-scanner")
include(":features:feature-participants")
include(":features:feature-dashboard")
include(":features:feature-more")
include(":features:feature-add-order")

// Modules hidden/removed for simplification
// include(":features:feature-walkin")
// include(":features:feature-inhub")
// include(":features:feature-speakers")
// include(":features:feature-sponsors")
