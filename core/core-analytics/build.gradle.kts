plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)
}

android {
    namespace = "pl.medidesk.mobile.core.analytics"
}

dependencies {
    // `api` (not implementation) — MdApplication needs PostHogAndroid.setup() at bootstrap;
    // only one place outside core-analytics imports PostHog directly.
    api(libs.posthog.android)

    // Force PostHog + Hilt transitive deps to versions we have cached.
    // Without these, gradle tries to fetch older pom files (lifecycle 2.5.1,
    // coroutines 1.6.1, okhttp 4.11.0) that aren't in the local cache.
    constraints {
        implementation("androidx.lifecycle:lifecycle-common-java8:2.8.7")
        implementation("androidx.lifecycle:lifecycle-process:2.8.7")
        implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
        implementation("androidx.lifecycle:lifecycle-common:2.8.7")
        implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
        implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
        implementation("androidx.core:core:1.15.0")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0")
        implementation("com.squareup.okio:okio:3.9.0")
        implementation("com.squareup.okio:okio-jvm:3.9.0")
    }
}
