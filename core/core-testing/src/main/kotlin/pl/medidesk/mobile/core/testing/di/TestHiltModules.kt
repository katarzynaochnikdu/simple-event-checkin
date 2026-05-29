package pl.medidesk.mobile.core.testing.di

/**
 * Placeholder for Hilt test modules (WO-MOB-024, Faza 0).
 *
 * Faza 3: add `@Module @TestInstallIn(components = [SingletonComponent::class], replaces = [...])`
 * modules here to swap production bindings (e.g. real repositories / Retrofit services) for fakes
 * in instrumented / Robolectric Hilt tests. Intentionally empty for now — `hilt-android-testing`
 * is already wired as an `api` dependency of this module.
 */
internal object TestHiltModules
