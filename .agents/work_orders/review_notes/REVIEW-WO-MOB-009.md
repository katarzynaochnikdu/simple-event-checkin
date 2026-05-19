# Review — WO-MOB-009

**Data:** 2026-05-19
**Worker:** worker-implementer
**Status:** Implementacja gotowa, QA manualne po stronie usera (mobile build)

## DoD check

- [x] Po tap "Tak, Check-In" zielony "WEJŚCIE OK" wyświetlany jako window-level Dialog (z-order ponad bottomBar AppNavHost-a) — implementacja kodu OK, weryfikacja wizualna manualna
- [x] Czerwony "Niepotwierdzone wejście" symetrycznie jako window-level Dialog — ten sam Composable, identyczny mechanizm
- [x] Oba warianty używają tego samego mechanizmu prezentacji (`Dialog` z `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true, dismissOnClickOutside = false)`)
- [x] Gradle build PASS — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL 3m28s, brak nowych warningów
- [x] Review note (ten plik)

## Zmienione pliki

1. `features/feature-scanner/.../screen/ScannerScreen.kt` — `ScanResultOverlay` z in-tree `AnimatedVisibility { Box(fillMaxSize) }` na `Dialog(properties = ...)`. Wnętrze (kolor SUCCESS/ERROR, ikony, Undo) zachowane 1:1. Dodano parametr `onDismiss` mapowany na `viewModel.dismissFeedback()`.
2. `features/feature-scanner/.../viewmodel/ScannerViewModel.kt` — ujednolicenie auto-dismiss timing dla DENIED (1500ms → 3000ms, symetria z SUCCESS). Dodana publiczna `dismissFeedback()` dla Dialog onDismissRequest (back press).

## Decyzja architektoniczna

Mechanizm prezentacji overlay'ów feedbackowych dla check-in: **Compose `Dialog` z `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)`** — renderuje w osobnym android Window, z-order gwarantowany ponad Scaffold/bottomBar/system bars. In-tree `Box(fillMaxSize)` był klipowany przez contentPadding outer Scaffolda → bug wizualny.

## Gates

- **QA mobile:** SKIPPED (worker-qa nie obsługuje Android Compose; manual user gate)
- **Security:** SKIPPED (czysto klient-side UI, brak API/auth/PII)
- **Contract sync:** SKIPPED (brak zmian API/types)
- **Migration:** SKIPPED (brak SQL)
- **Snapshot:** SKIPPED na życzenie usera (small mobile UI fix)

## Postmortem (gotcha do propagacji)

→ propozycja wpisu do `simple-event-checkin/.agents/` (lub `.agents/context/known_gotchas.md` jeśli wspólne dla całego projektu):

> **Compose overlay nad Scaffold(bottomBar):** in-tree `AnimatedVisibility { Box(fillMaxSize) }` renderowane WEWNĄTRZ Scaffold contentu jest klipowane do contentPadding tego Scaffolda — overlay leży pod bottomBar mimo `fillMaxSize`. Dla feedback-overlay'ów które MUSZĄ nakryć cały ekran (w tym tab bar / system bars) — używaj `Dialog` z `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)`. Dialog renderuje się w osobnym android Window i z-order jest gwarantowany ponad głównym oknem. Precedens: `ScannerScreen.ScanResultOverlay` (WO-MOB-009, 2026-05-19).
