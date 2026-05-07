// Top-level build file — configuration for all subprojects.
// ksp and hilt MUST be here with apply false so the HiltConventionPlugin in build-logic
// can apply them into the correct (root) classloader, avoiding KspTaskJvm ClassNotFound.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
