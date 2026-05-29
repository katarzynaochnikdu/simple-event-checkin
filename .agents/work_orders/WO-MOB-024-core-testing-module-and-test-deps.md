# WO-MOB-024: core-testing module + test deps in version catalog (Faza 0)

**Data:** 2026-05-28
**Worker:** Implementer (Kotlin/Gradle)
**Stage:** Testing Strategy — Faza 0 (Fundament i sprzątanie) — mobile
**Priorytet:** Normalny
**Scope:** `simple-event-checkin/` (Android native, branch `master`)

> Przenumerowany z pierwotnego WO-MOB-019 (kolizja — Twój mobile sięga WO-MOB-023). Mobile release v1.0.2 domknięty → working tree czysty.

## Cel
Postawić fundament testowy mobile per `TESTING_STRATEGY.md` §4: NOWY moduł `core/core-testing/` (TestDispatchers, MainDispatcherRule, MockApiDispatcher + factories/fakes/di skeletony) + test deps w `gradle/libs.versions.toml` + rejestracja w `settings.gradle.kts`. **ZERO testów** — moduł dostarcza util'e, faktyczne testy (ViewModel/UseCase/DAO/Compose) dochodzą w **Fazie 3**.

**Świadomy defer (by NIE ruszać ryzykownie build-logic):** convention plugin `commonTestDependencies()` (auto-wiring test deps do każdego modułu) → **Faza 3**. W Fazie 0 moduły konsumujące dodadzą `testImplementation(project(":core:core-testing"))` ręcznie gdy piszemy testy.

## Zakres
- `simple-event-checkin/gradle/libs.versions.toml` (dodać versions + libraries + bundle dla test deps)
- `simple-event-checkin/settings.gradle.kts` (dodać `include(":core:core-testing")`)
- `simple-event-checkin/core/core-testing/build.gradle.kts` (NEW)
- `simple-event-checkin/core/core-testing/src/main/kotlin/pl/medidesk/mobile/core/testing/` (NEW skeleton classes)
- (jeśli wymagane) `core/core-testing/src/main/AndroidManifest.xml` (minimal, jeśli android lib tego wymaga)

## Czego NIE ruszać 🛑
- ❌ Kod produkcyjny (`app/`, istniejące `core/*`, `features/*` poza dodaniem ich do settings — NIE). Tylko NOWY moduł + katalog + settings.
- ❌ `build-logic/` convention plugins — NIE modyfikować (defer commonTestDependencies do Fazy 3). core-testing używa istniejących `md.android.library` + `md.hilt`.
- ❌ NIE bumpować istniejących wersji w katalogu (kotlin 2.2.10, agp 9.2.1, hilt 2.59.2, coroutines 1.9.0, okhttp 4.12.0). Tylko DODAĆ nowe (mockk, turbine, mockwebserver — reuse okhttp ref, coroutines-test — reuse coroutines ref, hilt-android-testing — reuse hilt ref).
- ❌ NIE pisać testów (Faza 3). Skeleton util'e to NIE testy (to main source set infra).
- ❌ NIE commitować/pushować bez Krok 6.7.

## Pliki startowe
- `core/core-sync/build.gradle.kts` (wzorzec modułu: `alias(libs.plugins.md.android.library)` + `alias(libs.plugins.md.hilt)`, namespace, deps).
- `gradle/libs.versions.toml` (sekcje [versions]/[libraries]/[plugins]; test deps obecne: junit/junitExt/espresso/compose-ui-test).
- `settings.gradle.kts` (include core modules ~L31-38).
- `TESTING_STRATEGY.md` §4 (mobile core-testing), §5.3 (Room in-memory / MockWebServer / Turbine), §7.1.

## Treść do utworzenia (propozycje — implementer finalizuje wersje na najnowsze kompatybilne)

### `libs.versions.toml` — [versions] (DODAĆ)
```
mockk = "1.13.13"
turbine = "1.2.0"
robolectric = "4.14.1"
truth = "1.4.4"
# coroutines-test reuse: coroutines = "1.9.0" (już jest)
# mockwebserver reuse: okhttp = "4.12.0" (już jest)
# hilt-android-testing reuse: hilt = "2.59.2" (już jest)
```
### [libraries] (DODAĆ)
```
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
```
### [bundles] (DODAĆ — jeśli sekcji brak, utwórz)
```
unit-test = ["junit", "mockk", "turbine", "kotlinx-coroutines-test"]
```

### `settings.gradle.kts` — dodać przy core modules
```
include(":core:core-testing")
```

### `core/core-testing/build.gradle.kts` (NEW)
```kotlin
plugins {
    alias(libs.plugins.md.android.library)
    alias(libs.plugins.md.hilt)
}

android {
    namespace = "pl.medidesk.mobile.core.testing"
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))

    // Test utilities exposed via `api` so consuming modules' test source sets get them transitively
    api(libs.junit)
    api(libs.mockk)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    api(libs.okhttp.mockwebserver)
    api(libs.hilt.android.testing)
    implementation(libs.kotlinx.coroutines.android)
}
```
> Jeśli `md.android.library` ustawia `consumerProguard`/minify lub coś koliduje — dostosuj minimalnie. core-testing to lib utility, brak UI.

### Skeleton classes w `src/main/kotlin/pl/medidesk/mobile/core/testing/`
1. **`MainDispatcherRule.kt`** — JUnit `TestWatcher` podmieniający `Dispatchers.Main` na `UnconfinedTestDispatcher` (standardowy ~15 LOC, `@OptIn(ExperimentalCoroutinesApi)`).
2. **`TestDispatchers.kt`** — helper provider `TestScope`/`StandardTestDispatcher` (cienki wrapper).
3. **`http/MockApiDispatcher.kt`** — szkielet `okhttp3.mockwebserver.Dispatcher` zwracający 200/empty (placeholder, rozbudowa Faza 3).
4. **`di/TestHiltModules.kt`** — pusty placeholder z komentarzem (Faza 3: `@TestInstallIn` replace modules).
5. **`factories/Factories.kt`** — placeholder `// companion-object builders (makeParticipant, makeEvent...) — Faza 3`.
6. **`fakes/Fakes.kt`** — placeholder `// fake repositories/datasources — Faza 3`.

> Jeśli android-library wymaga `AndroidManifest.xml` — minimalny `<manifest />` w `src/main/`.

## Ryzyko
- **Build-logic / Gradle breakage** → mitygacja: NIE ruszamy build-logic; core-testing kopiuje wzorzec core-sync; weryfikacja `./gradlew :core:core-testing:assembleDebug`.
- **Wersje deps niekompatybilne (mockk/robolectric vs kotlin 2.2.10 / agp 9.2.1)** → mitygacja: implementer dobiera najnowsze kompatybilne; jeśli robolectric/truth sprawiają problem — pomiń je (opcjonalne, nieużywane w skeletonach), zostaw mockk/turbine/coroutines-test/mockwebserver/hilt-testing (te są kluczowe).
- **`./gradlew` wymaga Android SDK** → env ma SDK (user buildował APK v1.0.2). Jeśli mimo to build env-blocked → raport code-review + `compileDebugKotlin` jako fallback.
- mobile = osobny submoduł (commit + push czeka na Krok 6.7).

## Definition of Done ✅
- [ ] `libs.versions.toml`: test deps dodane (mockk, turbine, coroutines-test, mockwebserver, hilt-android-testing; robolectric/truth opcjonalne) + bundle.
- [ ] `settings.gradle.kts`: `include(":core:core-testing")`.
- [ ] `core/core-testing/build.gradle.kts` (md.android.library + md.hilt, namespace, test deps jako `api`).
- [ ] Skeleton: MainDispatcherRule, TestDispatchers, http/MockApiDispatcher, di/TestHiltModules, factories/, fakes/ (kompilują się).
- [ ] Istniejące wersje NIE bumpowane; build-logic NIETKNIĘTY.
- [ ] ZERO testów (skeletony = main source infra).

## Test akceptacyjny 🧪 (CLI)
1. `cd simple-event-checkin && ./gradlew :core:core-testing:assembleDebug` → **BUILD SUCCESSFUL** (moduł kompiluje z deps).
2. `./gradlew projects` → `:core:core-testing` widoczny na liście.
3. `./gradlew test` → BUILD SUCCESSFUL (0 nowych testów; istniejące — jeśli są — zielone; brak regresji w innych modułach).
4. `git -C simple-event-checkin status --short` → nowy moduł + katalog + settings; istniejące moduły/build-logic bez zmian.

> Jeśli `./gradlew` env-blocked: fallback `:core:core-testing:compileDebugKotlin` + raport; w ostateczności code-review że build.gradle.kts mirroruje core-sync + skeletony są poprawnym Kotlinem.

## Format zwrotki
- Lista nowych plików (core-testing) + edycje (libs.versions.toml, settings.gradle.kts).
- Finalne wersje dodanych test deps + czy robolectric/truth weszły czy pominięte.
- Output `./gradlew :core:core-testing:assembleDebug` + `./gradlew projects` (dowód moduł skompilowany + zarejestrowany).
- `git -C simple-event-checkin status --short` + potwierdzenie build-logic/istniejące moduły nietknięte.
