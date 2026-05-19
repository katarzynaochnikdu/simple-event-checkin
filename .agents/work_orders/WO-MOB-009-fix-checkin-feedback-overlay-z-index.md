# WO-MOB-009: Fix — feedback check-in (zielony/czerwony) zawsze jako top-level overlay

**Data:** 2026-05-19
**Worker:** worker-implementer (mobile / Kotlin Compose)
**Stage:** Mobile UX polish
**Priorytet:** Wysoki (regresja wizualna check-in flow — uczestnik/operator widzi feedback obcięty przez tab bar)
**Scope:** mobile (simple-event-checkin)
**Sizing:** 🟢 mały (≤3 pliki, 1 warstwa UI Compose)

## Cel
Po potwierdzeniu check-in ("Tak, Check-In" w dialogu Potwierdzenie Check-In) wynikowy feedback — zielony ekran "Wpuszczono" LUB czerwony ekran "Odmowa / Nie wchodzi" — ma być wyświetlany jako top-level overlay (poziom Modal/Dialog/Portal) nad całą UI (włącznie z tab barem). Obecnie czerwony wariant renderuje się pod widokiem / pod tab barem, podczas gdy zielony jest na wierzchu — należy ujednolicić symetrycznie oba warianty.

## Zakres
- `features/feature-scanner/src/main/java/pl/medidesk/mobile/feature/scanner/presentation/screen/ScannerScreen.kt` — refaktor `ScanResultOverlay` (linie 212-339): z in-tree `AnimatedVisibility { Box(fillMaxSize) }` na `Dialog(...)` z `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = true, dismissOnClickOutside = false)`. Wnętrze (treść kolorystyczna + przycisk Undo) zostaje bez zmian — opakowujemy tylko mechanizm prezentacji.
- (Opcjonalnie) `features/feature-scanner/src/main/java/pl/medidesk/mobile/feature/scanner/presentation/viewmodel/ScannerViewModel.kt` — tylko jeśli auto-dismiss timing dla DENIED różni się od SUCCESS, ujednolicić (poza tym BEZ zmian).

## Czego NIE ruszać 🛑
- `app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt` — outer `Scaffold(bottomBar = ...)` zostaje bez zmian. Nie przenosimy overlayu do hosta nawigacji (research odrzucił tę alternatywę).
- `ScanConfirmDialog` (`ScannerScreen.kt:117`) i `ParticipantDetailsScreen.kt` confirm dialog — działają poprawnie (Window-level AlertDialog), NIE refaktorować.
- `ScannerViewModel` logika check-in / Undo / queueing — wyłącznie ewentualny timing reset feedbacku, nic więcej.
- Snackbar w `ParticipantDetailsScreen` (linie 73-93) — out of scope (oddzielna ścieżka, manual check-in z listy).
- Material3 theme / kolory `ScanSuccess` / `ScanError` — bez zmian, tylko mechanizm prezentacji.

## Pliki startowe
- `simple-event-checkin/features/feature-scanner/src/main/java/pl/medidesk/mobile/feature/scanner/presentation/screen/ScannerScreen.kt` (główny — linie 60-114 host, 212-339 overlay do refaktoru)
- `simple-event-checkin/features/feature-scanner/src/main/java/pl/medidesk/mobile/feature/scanner/presentation/viewmodel/ScannerViewModel.kt` (~linia 184 — weryfikacja symetrii timing reset feedbacku SUCCESS vs DENIED)
- `simple-event-checkin/app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt` (linie 293-378 — read-only kontekst dla zrozumienia hierarchii Scaffold/bottomBar)

## Ryzyko
- **Backstack / system back:** Dialog reaguje na back press → ustawić `dismissOnBackPress = true` z mapowaniem na `viewModel.dismissFeedback()` (lub równoważne). Sprawdzić czy nie wywoła to dwukrotnego dismissu.
- **Tap-outside:** `dismissOnClickOutside = false` — feedback powinien dismiss'ować się tylko przez auto-timer LUB tap na Undo, nie przez przypadkowy tap obok.
- **Animacja:** dotychczasowa `AnimatedVisibility` (slide/fade) zniknie — Dialog ma własną system fade animation. Akceptowalne (research rekomenduje), ale jeśli UX wymaga slide-in, rozważyć custom transition wewnątrz Dialogu.
- **System bars / insety:** `decorFitsSystemWindows = false` może spowodować że overlay wjedzie pod status bar — należy zachować dotychczasowe padding/inset logic z `Box(fillMaxSize)`.
- **Camera lifecycle:** podczas wyświetlenia Dialogu kamera CameraX nadal działa pod spodem — sprawdzić czy nie startuje kolejny skan zanim feedback zniknie (ScannerViewModel powinien blokować — to obecne zachowanie).

## Definition of Done ✅
- [ ] Po tap "Tak, Check-In" zielony "Wpuszczono" pojawia się jako overlay nad całą UI (w tym tab barem dolnym)
- [ ] Czerwony "Odmowa / Nie wchodzi" pojawia się jako overlay nad całą UI (w tym tab barem dolnym)
- [ ] Oba warianty używają tego samego mechanizmu prezentacji (Modal/Dialog/Portal/nawigacja prezentacyjna) — symetria
- [ ] Build mobile przechodzi
- [ ] Review note w `review_notes/`

## Test akceptacyjny 🧪
1. Uruchom aplikację, zaloguj się, wybierz event z uczestnikami.
2. Otwórz skaner / wyszukaj uczestnika (np. Piotr Przybył, bilet *Connect +).
3. W dialogu "Potwierdzenie Check-In" tap "Tak, Check-In" — uczestnik wchodzi.
   - Oczekiwane: zielony overlay "Wpuszczono" nakrywa CAŁY ekran, w tym dolny tab bar.
4. Powtórz dla uczestnika, który NIE może wejść (np. brak opłaty / już zameldowany / inna reguła odmowy).
   - Oczekiwane: czerwony overlay "Odmowa" nakrywa CAŁY ekran symetrycznie do zielonego.
5. Oba overlaye znikają jednakowym mechanizmem (auto-dismiss / tap to dismiss — wg ustalonej zasady).

## Oczekiwany efekt wizualny 🖼️
- Zielony ekran sukcesu "Wpuszczono" — fullscreen overlay, nad tab barem.
- Czerwony ekran odmowy "Nie wchodzi" — fullscreen overlay, nad tab barem (obecnie pojawia się POD — to bug do naprawy).
- Tło sceny pod overlay'em pozostaje stabilne (nie "wycieka" przyciemnienie poprzedniego feedbacku, jak na screenshot'cie od użytkownika gdzie czerwony tinted background był widoczny pod kolejnym dialogiem).

## Kontrakt API 🔗
N/A — zmiana czysto klient-side (prezentacja UI).

## Kontekst wizualny
Zrzut ekranu od użytkownika: dialog "Potwierdzenie Check-In" dla Piotr Przybył (pictr.przybyl@diag.pl, Bilet: *Connect +), przyciski Anuluj / Tak, Check-In. Tło sceny przyciemnione/czerwone — sugeruje że poprzedni feedback (czerwony) wyrenderował się POD obecnym widokiem zamiast na wierzchu.

## Format zwrotki
- Lista zmienionych plików z jednolinijkowym opisem zmian
- Git diff summary
- Screenshot before/after (zielony overlay + czerwony overlay) jako dowód wizualnego efektu
- Wynik komendy weryfikacyjnej (Gradle build)
- Propozycja wpisu do `decision_log.md` (jeśli wybrano konkretny mechanizm prezentacji jako kanon — np. "feedback check-in zawsze przez Dialog z `usePlatformDefaultWidth=false`")
