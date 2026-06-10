# WO-MOB-028: Logout wipe — czyszczenie Room DB (cache PII) przy wylogowaniu / zmianie usera

**Data:** 2026-06-10
**Worker:** `worker-implementer`
**Stage:** Mobile Security Audit Sprint 2 — Faza 2.5 (inline remediation)
**Finding:** F2A-001 (WYSOKIE, NOWE) — [F2A_STORAGE_PLATFORM_REVIEW.md](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2A_STORAGE_PLATFORM_REVIEW.md)
**Status:** ✅ DONE 2026-06-10 (implementer) — LogoutUseCase w **core-sync** (NIE core-data — moduł nie istnieje; core-sync jedyny widzi MdDatabase+AuthDataStore+Analytics, konsumenci już od niego zależą). Cykl AuthInterceptor rozwiązany portem `SessionWipeHook` (core-network) + adapter @Binds (core-sync); świadomie NIE przez `SessionManager.sessionExpired` (SharedFlow replay=0 — ginie bez kolektora w background sync). 5 ścieżek wpiętych; pending queue: best-effort `runImmediateSyncAndWait` ≤5s TYLKO manual logout, potem wipe; `finally`: clearAll+Analytics.reset nawet gdy Room rzuci. Odchylenia: SyncEngine.cancelImmediateSync() +10 (straggler po wipe → 401-pętla; WorkManager nie mockowalny), core-sync build.gradle +9 (room-runtime implementation + core-testing). **assembleDebug BUILD SUCCESSFUL 2m22s; testy 5/5 PASS; grep: zero ścieżek logout bez wipe.** +328 LOC nowych / +65/-12 edycji. COMMITTED mobile `09a1300` (2026-06-10, pushed). Gotchas-kandydaci: Norton MITM TLS vs JBR cacerts; mockkStatic vs WorkManager 2.10; room-runtime jako implementation nie eksportuje RoomDatabase; sessionExpired replay=0.
**Snapshot:** `snapshot/pre-mobile-security-sprint-2-remediation-2026-06-10` @ mobile `1876fbe`
**Sizing:** 🟡 średni (6 plików, 1 warstwa — mobile Kotlin)

---

## Cel

Logout (każda z 5 ścieżek) MUSI czyścić cały lokalny cache PII: Room `md_checkin.db` (`clearAllTables()`) — obecnie czyszczone jest TYLKO `AuthDataStore.clearAll()` (EncryptedSharedPreferences). Skutki obecnego stanu: (1) cross-user PII bypass na wspólnym urządzeniu (cache-first render omija `require_mobile_event_access`), (2) PII at-rest po wylogowaniu bezterminowo, (3) misatrybucja audytu — pending offline queue operatora A wypchnięta pod JWT operatora B.

## Zakres — pliki

1. **NOWY** `LogoutUseCase` (lokalizacja: `core/core-data` — obok repository layer; wstrzykiwalny Hilt): `suspend operator fun invoke()` → `syncEngine.stopPeriodicSync()` → `mdDatabase.clearAllTables()` (IO dispatcher) → `authDataStore.clearAll()` → `Analytics.reset()` (jeśli dostępny w tym module — sprawdź zależności; jeśli nie, zostaw reset w call-site'ach).
2. `feature-auth/.../AuthRepositoryImpl.kt:35-37` — `logout()` używa LogoutUseCase (lub równoważnie woła clearAllTables — zachowaj istniejący kontrakt publiczny).
3. `feature-more/.../SettingsViewModel.kt:104-110` — logout → LogoutUseCase.
4. `feature-more/.../MoreViewModel.kt:44-49` — logout → LogoutUseCase.
5. `core-network/.../AuthInterceptor.kt:32-36` — auto-logout 401: tu UWAGA na zależności cykliczne (core-network nie może zależeć od core-data?) — jeśli cykl, zastosuj callback/event (np. istniejący mechanizm) albo wstrzyknij lambda; clearAllTables musi się wykonać także na tej ścieżce.
6. `app/navigation/AppNavHost.kt:88` — nieudany auth-check → LogoutUseCase.

**Decyzja o pending queue (offline_checkins / walkin_participants / speaker_checkin_queue):** wariant MINIMAL bezpieczny — przy logout czyścimy WSZYSTKO (w tym pending) — misatrybucja audytu jest gorsza niż utrata niezsynchronizowanych check-inów; PRZED wipe spróbuj jednego best-effort `triggerImmediateSync` z krótkim timeoutem (≤5s) TYLKO na ścieżce manualnego logout (Settings/More), NIE na 401 auto-logout (token martwy — sync i tak padnie). Jeśli istniejący SyncEngine nie wspiera awaitable sync z timeoutem — pomiń best-effort, czysty wipe + komentarz.

## Czego NIE ruszać 🛑

- NIE zmieniaj schematu Room (zero migracji), NIE dotykaj backend, NIE zmieniaj semantyki `AuthDataStore.clearAll()`.
- NIE commituj (commit = decyzja usera po raporcie końcowym).
- NIE dotykaj `.agents/` poza tym WO.

## Test akceptacyjny 🧪

1. Test jednostkowy `test_logout_clears_room_cache` (moduł core-testing z WO-MOB-024 lub test ViewModel z MockK): po wywołaniu logout — `clearAllTables()` invoked (verify MockK) + `authDataStore.clearAll()` invoked.
2. Kompilacja: `./gradlew :app:assembleDebug` PASS (lub `compileDebugKotlin` wszystkich modułów).
3. Code-review QA: wszystkie 5 ścieżek logout przechodzi przez wipe (grep `clearAll()` bez `clearAllTables` w sąsiedztwie = 0 poza LogoutUseCase).

## Definition of Done

- [ ] LogoutUseCase + 5 ścieżek wpięte
- [ ] Min. 1 test (wzorzec AAA, nazwa EN)
- [ ] assembleDebug PASS
- [ ] Zero zmian poza zakresem
