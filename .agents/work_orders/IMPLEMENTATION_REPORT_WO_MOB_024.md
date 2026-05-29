# IMPLEMENTATION REPORT — WO-MOB-024 (core-testing module + test deps, Faza 0)

**WO:** [WO-MOB-024](WO-MOB-024-core-testing-module-and-test-deps.md)
**Strategia:** TESTING_STRATEGY.md — Faza 0 (mobile)
**Data:** 2026-05-28
**Status:** ✅ IMPLEMENTED + config-verified — **NIEZACOMMITOWANE** (czeka: decyzja o commicie; mobile NIE deployuje z git, więc brak ryzyka prod — ale `assembleDebug` env-blocked, patrz Weryfikacja)

## Cel
Fundament testowy mobile: NOWY moduł `core/core-testing/` + test deps w `libs.versions.toml` + rejestracja. ZERO testów (util'e w main source; testy w Fazie 3). Convention plugin `commonTestDependencies()` świadomie odłożony do Fazy 3 (by nie ruszać build-logic).

## Co zrobione
**NEW moduł `core/core-testing/`:**
- `build.gradle.kts` — `md.android.library` + `md.hilt`, `namespace="pl.medidesk.mobile.core.testing"`, `implementation(project(":core:core-model"/":core:core-network"))`, test utils jako `api(...)`: junit, mockk, turbine, kotlinx-coroutines-test, okhttp-mockwebserver, hilt-android-testing + `implementation(kotlinx.coroutines.android)`.
- Skeletony (main source, kompilowalny Kotlin): `MainDispatcherRule.kt` (TestWatcher Dispatchers.Main→UnconfinedTestDispatcher), `TestDispatchers.kt` (standard/unconfined/scope), `http/MockApiDispatcher.kt` (MockWebServer Dispatcher 200/empty placeholder), `di/TestHiltModules.kt` (placeholder), `factories/Factories.kt` + `fakes/Fakes.kt` (placeholdery). Brak `AndroidManifest.xml` (jak core-sync/core-model — AGP syntetyzuje przy ustawionym namespace).

**`libs.versions.toml`** (+23 linie, additive): [versions] mockk 1.13.13, turbine 1.2.0, robolectric 4.14.1, truth 1.4.4. [libraries] mockk, mockk-android, turbine, kotlinx-coroutines-test (ref=coroutines REUSE), okhttp-mockwebserver (ref=okhttp REUSE), hilt-android-testing (ref=hilt REUSE), robolectric, truth. [bundles] unit-test. **Zero bumpów istniejących wersji.**

**`settings.gradle.kts`** — `include(":core:core-testing")`.

## Definition of Done
| Kryterium | Status |
|---|---|
| libs.versions.toml test deps + bundle | ✅ (robolectric/truth weszły — brak konfliktu) |
| settings include core-testing | ✅ |
| core-testing build.gradle.kts (md.android.library+hilt) | ✅ |
| skeletony kompilowalny Kotlin | ✅ (config-verified; pełny compile env-blocked) |
| build-logic NIETKNIĘTY; istniejące wersje nie bumpowane | ✅ |
| ZERO testów | ✅ |

## Weryfikacja
- ✅ `./gradlew projects` → BUILD SUCCESSFUL, `:core:core-testing` zarejestrowany.
- ✅ `./gradlew :core:core-testing:help` → BUILD SUCCESSFUL (build.gradle.kts + wszystkie `libs.*` aliasy + settings parsują się; aliasy rozwiązane do poprawnych koordynatów `io.mockk:mockk:1.13.13` itd.).
- ✅ `./gradlew :core:core-model:compileDebugKotlin --offline` (nietknięty moduł) → BUILD SUCCESSFUL = zero regresji z edycji katalogu/settings.
- ⚠️ `./gradlew :core:core-testing:assembleDebug` → **ENV-BLOCKED**: host ma TLS interception (MITM) — pobranie 5 nowych deps z Maven Central / dl.google.com pada na `SSL handshake (certificate_unknown) PKIX path building failed`. Deps nie w cache → compile skeletonów nieuruchomiony. **To limit środowiska, NIE defekt kodu** (offline run potwierdził: jedyny bloker = download, aliasy poprawne). Persystuje z wyłączonym sandboxem → machine-level.

## Gates
QA: config-level PASS (projects/help/offline-compile). Full compile deferred (env). Security/Contract/Migration N/A.

## Snapshot
`snapshot/pre-mobile-core-testing-faza0-2026-05-28` @ mobile `47a65d1` + monorepo `9be4214`.

## ⚠️ Do zweryfikowania online (gdy host bez TLS MITM / deps w cache)
`cd simple-event-checkin && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew :core:core-testing:assembleDebug` → spodziewane BUILD SUCCESSFUL (skeletony to standardowy Kotlin). Jeśli padnie — izolowane do nowego modułu, łatwy fix.

## Postmortem
1. **Gotcha (pending known_gotchas.md):** git-bash `./gradlew` wymaga `JAVA_HOME` (Android Studio JBR, brak `java` na PATH) + host TLS interception blokuje pobieranie NOWYCH deps z Maven (tylko cache działa) → nowy dep niecachowany = build fail na resolution nawet z sandboxem off (machine-level MITM). Dowód poprawności bez downloadu: `:<module>:help` (config + aliasy) + offline-compile cachowanego modułu (brak regresji).
2. **Security flag:** `simple-event-checkin/local.properties` (untracked, gitignored) zawiera live secrets (POSTHOG_API_KEY + keystore passwords). NIE tknięty/stageowany — tylko odczyt `sdk.dir`. FYI.
3. **Follow-up Faza 3:** convention plugin `commonTestDependencies()` w build-logic; faktyczne testy (ViewModel/UseCase/DAO Room in-memory/Compose UI) + per-module `testImplementation(project(":core:core-testing"))`.
