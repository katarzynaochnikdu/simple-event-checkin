# WO-MOB-010: Fix UNDONE color + remove "Cofnij wejście" button from ScanResultOverlay

**Data:** 2026-05-20
**Scope:** mobile (simple-event-checkin)
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — Krytyczny / Wysoki / Normalny / Niski]

## Cel
Kontynuacja WO-MOB-009. Dwie zmiany w `ScanResultOverlay` (ScannerScreen.kt):
(1) Stan **UNDONE** ("COFNIĘTO" po użyciu "Cofnij wejście") wyświetla się dziś na zielonym tle (ScanSuccess) — operator nie odróżnia "WEJŚCIE OK" od "COFNIĘTO". Powinien być **czerwony** (ScanError), semantycznie cofnięcie wejścia = denied/exit state.
(2) Usunąć przycisk **"Cofnij wejście"** z wariantu SUCCESS overlay'a — overlay auto-dismiss'uje się po 3s, przypadkowy klik powoduje niezamierzone cofnięcie. Cofanie wejścia ma być dostępne wyłącznie z listy uczestników (ParticipantDetailsScreen lub odpowiednik).

## Zakres
- `simple-event-checkin/features/feature-scanner/src/main/java/pl/medidesk/mobile/feature/scanner/presentation/screen/ScannerScreen.kt`
  - switch koloru UNDONE z `ScanSuccess` na `ScanError`
  - usunięcie `OutlinedButton` "Cofnij wejście" z gałęzi SUCCESS w `ScanResultOverlay`
- (opcjonalnie) `ScannerViewModel` — sprawdzić czy `undoLastCheckin()` nadal jest wywoływany (prawdopodobnie TAK — z listy uczestników); jeśli nie ma żadnych innych callers → flag w raporcie, ale NIE usuwać w tym WO.

## Czego NIE ruszać 🛑
- Mechanizm `Dialog` z WO-MOB-009 (window-level prezentacja overlay'a) — bez zmian.
- Timing auto-dismiss 3000 ms — bez zmian.
- Ścieżka **undo z listy uczestników** (ParticipantDetailsScreen) — out of scope, tylko weryfikacja że nadal działa.
- Logika `undoLastCheckin()` w ViewModel/repo/use-case — bez zmian, tylko usunięcie wywołania z overlay button'a.

## Pliki startowe
- `ScannerScreen.kt` — sekcja `ScanResultOverlay` (gałąź SUCCESS + UNDONE) — kolor tła + przycisk.
- (opcjonalnie) `ScannerViewModel.kt` — gdzie wołany `undoLastCheckin()`.
- `ParticipantDetailsScreen` (lub odpowiednik) — weryfikacja że tamtejsza akcja undo nadal kompiluje się i działa.

## Ryzyko
- Zostawienie martwego kodu w ViewModel jeśli undo wołane było TYLKO z overlay'a (mało prawdopodobne — lista uczestników też używa).
- Regres dostępności undo: jeśli okaże się, że jedyna ścieżka undo była z overlay'a → zablokowanie cofania. Mitygacja: przed PR potwierdzić istnienie undo w ParticipantDetailsScreen.

## Definition of Done ✅
- [ ] Po cofnięciu wejścia overlay "COFNIĘTO" jest na **czerwonym** tle (`ScanError`).
- [ ] W overlay'u "WEJŚCIE OK" **nie ma** przycisku "Cofnij wejście" — tylko tekst (komunikat + imię/bilet) i auto-dismiss 3s.
- [ ] Cofanie wejścia z listy uczestników (ParticipantDetailsScreen lub odpowiednik) **nadal działa** — weryfikacja manualna.
- [ ] Build przechodzi (`./gradlew :app:assembleDebug` lub równoważnie).
- [ ] Brak warningów o nieużywanych importach po usunięciu przycisku.
- [ ] Review note w `review_notes/` (jeśli wymagane przez Mastera).

## Test akceptacyjny 🧪
1. Uruchom aplikację na urządzeniu/emulatorze, zaloguj się, wybierz event.
2. Zeskanuj prawidłowy bilet uczestnika → overlay "WEJŚCIE OK" pojawia się **bez przycisku "Cofnij wejście"**, tylko tekst + imię/bilet, po ~3s znika.
3. Przejdź do listy uczestników → szczegóły zameldowanego uczestnika → użyj akcji "Cofnij wejście" → overlay "COFNIĘTO" pojawia się na **czerwonym** tle (nie zielonym).
4. Po auto-dismiss overlay znika, status uczestnika na liście odzwierciedla cofnięcie.

## Oczekiwany efekt wizualny 🖼️
- **SUCCESS overlay** ("WEJŚCIE OK"): zielone tło (bez zmian), wyłącznie tekst + imię/numer biletu, **brak** outlined button'a "Cofnij wejście".
- **UNDONE overlay** ("COFNIĘTO"): **czerwone** tło (`ScanError`, zmiana z zielonego), ikona/tekst denied state — wizualnie odróżnialny od SUCCESS na pierwszy rzut oka.

## Kontrakt API
n/d — wyłącznie zmiana UI front-end mobile, bez ruchu na backendzie.

## Format zwrotki
- Lista zmienionych plików z jednolinijkowym opisem.
- Git diff summary (`ScannerScreen.kt`).
- Screenshoty: SUCCESS overlay (zielony, bez button'a) + UNDONE overlay (czerwony).
- Wynik build'a (`./gradlew assembleDebug`).
- Potwierdzenie że undo z listy uczestników nadal działa (krótki opis + screenshot lub log).
- Propozycja wpisu do `decision_log.md` (jeśli decyzja architektoniczna — np. "undo wyłącznie z details screen").
