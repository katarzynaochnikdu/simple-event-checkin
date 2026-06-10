# WO-MOB-034: Platform/build — NISK hardening bundle (FLAG_SECURE dialogs, ProGuard, manifest, intenty, QR, dead modules)

**Data utworzenia:** 2026-06-10 (scaffolded w Mobile Security Sprint 2, Faza 3)
**Worker:** worker-implementer
**Stage:** Mobile Sprint 2 Remediation — Faza 2 (NISK bundle #2)
**Priorytet:** 🟢 P2-P3 (1× gate-NISK + 6× NISK z F2A/F2B)
**Status:** **OPEN**
**Findings:** **N-3** ([gate 2.5](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2_5_REMEDIATION_SECURITY_GATE.md)) + **F2A-007 / F2A-008 / F2A-011 / F2A-012** ([F2A](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2A_STORAGE_PLATFORM_REVIEW.md)) + **F2B-005 / F2B-006** ([F2B](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2B_NETWORK_AUTH_TELEMETRY_REVIEW.md)) (+ **F2A-010** jeśli WO-MOB-029 = opcja B)

---

## Cel

Hurtowy platform/build hardening — 7 drobnych pozycji bez logiki biznesowej, jeden przebieg + jeden smoke release build.

## Zakres

1. **N-3 — FLAG_SECURE na oknach Dialog:** helper (np. `SecureDialogEffect` z `DialogWindowProvider`) ustawiający FLAG_SECURE na oknach Compose Dialog/ModalBottomSheet, gated `!BuildConfig.DEBUG`; zastosować w: AddOrderSheet, WalkinFormSheet, ChangePasswordDialog, dialogi ScannerScreen (lista z gate'u). Activity-level flaga (WO-MOB-030) zostaje bez zmian.
2. **F2A-007 — ProGuard:** `proguard-rules.pro:11-22` — usunąć martwą regułę Gson (`SerializedName` — projekt używa Moshi codegen), usunąć keep na encjach Room (`@androidx.room.Entity` — Room+KSP nie wymaga), zawęzić `keep core.network.**`/`core.model.**` do faktycznie reflektowanych DTO. **OBOWIĄZKOWY smoke release build po zmianie** (assembleRelease + instalacja + login + lista uczestników + skan).
3. **F2A-008 — manifest:** `app/src/main/res/xml/data_extraction_rules.xml` (exclude root; katalog xml/ nie istnieje — utworzyć) + `android:dataExtractionRules` w manifeście + `<uses-feature android:name="android.hardware.camera" android:required="true"/>`.
4. **F2A-011 — ACTION_VIEW allowlist:** helper `openExternalUrl(url)` akceptujący tylko `http(s)` + try/catch ActivityNotFoundException; zastosować w `SpeakerDetailScreen.kt:189,196,203` (socialLinkedin/Twitter/website z API) + `ConsentsForm.kt:56-57` (consent.url). `tel:`/`mailto:` konstruowane lokalnie — bez zmian.
5. **F2B-006 — QR charset:** `ScannerScreen.kt:511-517` — regex `^[A-Za-z0-9_-]{1,100}$` przed `onQrDetected` (defense-in-depth; limit 200 zostaje jako outer guard).
6. **F2B-005 — log hygiene:** usunąć `e.printStackTrace()` (`ScannerScreen.kt:530`); ungated `Log.*` bez PII (SpeakerCheckinRepository:93,97; LookupParticipantByTicketUseCase:51,64; ParticipantTagsRepository:44-49) → DEBUG-guard lub pozostawić z komentarzem (decyzja w WO; pełny Timber-refactor = poza scope).
7. **F2A-012 — dead modules (decyzja usera w DoR):** feature-walkin/feature-inhub/feature-sponsors (wykomentowane w `settings.gradle.kts` — NIE shipują) + martwy `core-ui/ImagePickerCropper.kt` + `MobileApiService.uploadImage` → wariant (a) przenieść do gitignored `.robocze/` (wzorzec checkin-app 2026-05-31) lub (b) komentarz-strażnik „re-enable WYMAGA security review (inhub=PIN, sponsors=financials+NIP)" w settings.gradle.kts.
8. **(warunkowy) F2A-010:** jeśli WO-MOB-029 rozstrzygnięty jako opcja B (ADR, custom scheme zostaje) — `Uri.encode(token)` w `Screen.kt:135-139` + walidacja charset tokena w `AppNavHost.kt:162-164` (crash-DoS spreparowanym linkiem). Przy opcji A — pozycja jedzie w WO-MOB-029.

## Czego NIE ruszać 🛑

- MainActivity FLAG_SECURE (WO-MOB-030) i konfiguracja PostHog (WO-MOB-031) — bez zmian.
- Walk-in data-path (WalkinEntity/Dao/SyncWorker) — shipuje i działa; usuwanie modułów dotyczy TYLKO niezalinkowanych feature-modułów (zweryfikować ponownie `settings.gradle.kts` + zero project deps przed ruchem).
- Wersje zależności (`libs.versions.toml`) — żadnych bumpów w tym WO.

## Test akceptacyjny 🧪

1. `./gradlew :app:assembleDebug` PASS + **`assembleRelease` PASS z instalacją** (smoke po ProGuard: login → lista uczestników → skan → speaker detail).
2. Release build: screenshot na otwartym AddOrderSheet/ChangePasswordDialog → zablokowany.
3. QR z znakiem spoza charset (np. emoji) → odrzucony przed lookupem.
4. Spreparowany link z `linkedin://` w polu social → NIE otwiera (allowlist http/s).
5. Grep: `printStackTrace` w features = 0.

## Sizing / Estymata

🟡 średni — ~2-3h (najwięcej: smoke release build po ProGuard).
