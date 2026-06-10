# WO-MOB-030: FLAG_SECURE — blokada screenshot/recents na ekranach PII

**Data:** 2026-06-10
**Worker:** `worker-implementer`
**Stage:** Mobile Security Audit Sprint 2 — Faza 2.5 (rider low-cost)
**Finding:** F2A-003 (ŚREDNIE — re-affirm MOB-MED-003 z MASVS Sprint 1) — [F2A_STORAGE_PLATFORM_REVIEW.md](../../../.agents/context/_audits/2026-06-mobile-security-sprint-2/F2A_STORAGE_PLATFORM_REVIEW.md)
**Status:** ✅ DONE 2026-06-10 (implementer) — MainActivity.kt +8 linii (import WindowManager + blok FLAG_SECURE po super.onCreate, przed enableEdgeToEdge/setContent, gated `!BuildConfig.DEBUG`). assembleDebug BUILD SUCCESSFUL 1m47s. Delta czysta (pliki WO-MOB-028 nietknięte). Test manualny screenshot-block = post-deploy na release buildzie (user). COMMITTED mobile `09a1300` (2026-06-10, pushed). **Nota Mastera:** finding ŚR wykonany jako low-cost auto-fix rider (rekomendacja F0+F2A) — odnotować w raporcie końcowym jako odstępstwo in-plus. Gotcha-kandydat: bash agenta nie dziedziczy JAVA_HOME (inline `JAVA_HOME=...jbr`); truststore Norton w %TEMP% ulotny.
**Snapshot:** `snapshot/pre-mobile-security-sprint-2-remediation-2026-06-10` @ mobile `1876fbe`
**Sizing:** 🟢 mały (1 plik)

---

## Cel

Zablokować screenshoty / nagrywanie ekranu / podgląd w recents dla całej aplikacji (single-Activity — pokrywa wszystkie 8 ekranów PII z listy F2A-003: Participants, ParticipantDetails, MyMentees, Speakers, SpeakerDetail, Stats, AddOrder sheet, Settings + Scanner po skanie).

## Zakres

`app/src/main/java/pl/medidesk/mobile/MainActivity.kt` — w `onCreate` PRZED `setContent`:

```kotlin
if (!BuildConfig.DEBUG) {
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
}
```

Gating `!BuildConfig.DEBUG` — w debug screenshoty zostają (potrzebne do dokumentacji QA — precedens WO-204 log-guards). Import `android.view.WindowManager`. UWAGA: użyj `BuildConfig` z pakietu app (sprawdź istniejące importy w MainActivity).

## Czego NIE ruszać 🛑

- Nic poza MainActivity.kt. NIE dodawaj per-screen logiki. NIE commituj.

## Test akceptacyjny 🧪

1. `./gradlew :app:assembleDebug` PASS.
2. Code-review: flaga ustawiana przed setContent, gated !DEBUG.
3. (Manualny, post-deploy — user): na release buildzie próba screenshotu → czarny ekran / komunikat systemowy.

## Definition of Done

- [ ] FLAG_SECURE w onCreate gated !DEBUG
- [ ] assembleDebug PASS
- [ ] Zero zmian poza MainActivity.kt
