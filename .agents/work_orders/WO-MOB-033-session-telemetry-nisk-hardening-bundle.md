# WO-MOB-033: Sesja/telemetria — NISK hardening bundle (gate follow-upy N-1/N-2 + residualy WO-MOB-031)

**Data utworzenia:** 2026-06-10 (scaffolded w Mobile Security Sprint 2, Faza 3)
**Worker:** worker-implementer
**Stage:** Mobile Sprint 2 Remediation — Faza 2 (NISK bundle #1)
**Priorytet:** 🟢 P2 (5× NISK — follow-upy z gate'u 2.5 + residualy)
**Status:** ✅ DONE 2026-06-10 (gate fali mobile 033+034 PASS 0/0/0/2; committed mobile wave) — N-1 `withContext(NonCancellable)` w finally LogoutUseCase (+ Analytics.optOut F2B-007b) · N-2 Coil cache wipe przez port `ImageCacheCleaner` (interfejs core-sync + @Provides impl w app — rozwiązuje cykl, pokrywa 5 ścieżek logout) · F2B-007a SettingsViewModel optOut/capture order · F2B-009 redactHeader Authorization+Cookie w NetworkModule DEBUG. assembleDebug PASS; LogoutUseCaseTest 6/6 (nowy guard cancelacji). 2 nowe pliki + 4 zmienione. **Follow-up flagga:** AppNavHost `saveAnalyticsConsent` ma analogiczny consent-race (poza scope). Gotcha-kandydaci: Coil clear() wymaga @OptIn(ExperimentalCoilApi); suspend w finally → NonCancellable. F2A-006 (deleteSynced) rider POMINIĘTY (opcjonalny).
**Findings:** **N-1 + N-2** ([F2_5_REMEDIATION_SECURITY_GATE.md](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2_5_REMEDIATION_SECURITY_GATE.md)) + **F2B-007 residualy + F2B-009 residual** ([F2B](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md)) + rider **F2A-006**

---

## Cel

Domknąć hardening wokół LogoutUseCase (WO-MOB-028) i telemetrii (WO-MOB-031) — 5 punktowych poprawek w spójnym scope sesja/consent/cache.

## Zakres (5 punktów)

1. **N-1:** `core/core-sync/.../LogoutUseCase.kt:67-74` — `finally` owinąć `withContext(NonCancellable) { ... }` (authDataStore.clearAll() jest suspend; cancelacja w trakcie clearAllTables() → CancellationException w finally → prefs-wipe + Analytics.reset() pominięte). Najtańszy fix z gate'u.
2. **N-2:** na ścieżce logout (LogoutUseCase) dodać `imageLoader.diskCache?.clear()` + `memoryCache?.clear()` (`app/di/CoilModule.kt:33` — cache 50MB; treści quasi-publiczne: zdjęcia speakerów/branding; zdjęcia uczestników NIE idą przez Coil — zweryfikowane w gate). Uwaga DI: ImageLoader żyje w module app — wstrzyknąć przez port (wzorzec SessionWipeHook) albo hook w app-warstwie.
3. **F2B-007 residual (a):** `features/feature-more/.../SettingsViewModel.kt:96-99` — zamienić kolejność: `capture(ANALYTICS_CONSENT_CHANGED)` PRZED `Analytics.optOut()` (dziś event ginie — SDK już opted-out; TELEMETRY.md deklaruje wysyłkę).
4. **F2B-007 residual (b):** LogoutUseCase — po `Analytics.reset()` wywołać `Analytics.optOut()` (logout czyści flagę consent w DataStore, ale SDK w bieżącym procesie zostaje opted-in do restartu) ALBO przestać czyścić flagę consent przy logout (zgoda nie jest per-konto) — decyzja w WO, jedna z dwóch.
5. **F2B-009 residual:** `core/core-network/.../NetworkModule.kt:35-39` — `redactHeader("Authorization")` na HttpLoggingInterceptor (DEBUG-only, ale JWT w lokalnym logcat dev-builda; 1 LOC).
6. **Rider F2A-006 (opcjonalny, jeśli zostaje czas):** wywołać `deleteSynced()` po udanym pushu w `SyncWorker` (`OfflineCheckinDao.kt:31-32`, `SpeakerCheckinDao.kt:46-47` — dziś 0 call sites) + DELETE synced walk-inów per event. Retencja in-session; logout wipe już domyka resztę.

## Czego NIE ruszać 🛑

- Semantyka 5 ścieżek logout i SessionWipeHook (WO-MOB-028) — tylko dokładamy NonCancellable/Coil/optOut, zero zmian kolejności wipe.
- Konfiguracja PostHog w MdApplication.kt (WO-MOB-031) — bez zmian.
- Schemat Room — zero migracji.

## Test akceptacyjny 🧪

1. Test: cancelacja coroutine podczas logout → `authDataStore.clearAll()` i `Analytics.reset()` MIMO TO wykonane (verify MockK).
2. Test/code-review: opt-out w Settings → event `analytics_consent_changed(action=opted_out)` JEST emitowany przed optOut.
3. Code-review: redactHeader obecny; grep `redactHeader` = 1.
4. `./gradlew :app:assembleDebug` PASS + istniejące testy LogoutUseCase (5) zielone.

## Sizing / Estymata

🟢 mały — ~1-2h.
