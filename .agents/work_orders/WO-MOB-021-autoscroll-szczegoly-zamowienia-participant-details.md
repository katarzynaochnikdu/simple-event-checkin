# WO-MOB-021: Autoscroll na sekcję „Szczegóły zamówienia" w karcie uczestnika

**Data:** 2026-05-28
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — Krytyczny / Wysoki / Normalny / Niski]
**Scope:** mobile (simple-event-checkin)
**Sizing:** 🟢 mały (1 plik, czysty Compose UI / scroll behavior)

## Cel
Na ekranie karty uczestnika (`ParticipantDetailsScreen`) po świadomym kliknięciu rozwinięcia sekcji „Szczegóły zamówienia" widok ma się automatycznie przewinąć tak, żeby cała rozwinięta tabelka była widoczna; po zwinięciu widok wraca na początek (do HERO). Manualne przewijanie musi nadal działać swobodnie również przy rozwiniętej tabelce — auto-scroll nie może blokować scrolla.

## Zakres
[Lista plików/modułów, które Worker MA PRAWO modyfikować]
- `simple-event-checkin/features/feature-participants/src/main/java/pl/medidesk/mobile/feature/participants/presentation/screen/ParticipantDetailsScreen.kt`

## Czego NIE ruszać 🛑
- Backend / API (zero zmian backendowych — czyste UI)
- Inne ekrany feature-participants poza `ParticipantDetailsScreen.kt`
- [do uzupełnienia]

## Pliki startowe
- `ParticipantDetailsScreen.kt` — ekran używa pojedynczego `Column` z `.verticalScroll(scrollState)` (`rememberScrollState`). Sekcja „Szczegóły zamówienia" to composable `OrderSection` z lokalnym `var expanded by remember { mutableStateOf(false) }` + `AnimatedVisibility(visible = expanded, ...)`.

## Kontekst techniczny / sugerowane podejście
- Podnieść stan `expanded` wyżej (do `ParticipantDetailsScreen`) lub dodać callback z `OrderSection`, aby ekran wiedział o zmianie.
- Użyć `coroutineScope.launch { scrollState.animateScrollTo(...) }` w `LaunchedEffect(expanded)` lub w `onClick`.
- Na expand: scroll do pozycji sekcji — np. measure przez `onGloballyPositioned` albo `scrollState.maxValue` po zakończonej animacji rozwijania.
- Na collapse: `animateScrollTo(0)` (powrót do HERO od góry).
- Auto-scroll WYŁĄCZNIE jako reakcja na świadomy klik użytkownika — nie może blokować swobodnego manualnego scrolla (np. nie wymuszać pozycji w sposób ciągły).

## Ryzyko
- Ryzyko, że auto-scroll „walczy" z manualnym scrollem użytkownika → mitygacja: trigger tylko na zmianę `expanded`, nie na ciągłą obserwację.
- Ryzyko race condition między animacją `AnimatedVisibility` a pomiarem pozycji (scroll do pozycji zanim layout urośnie) → mitygacja: scroll po zakończeniu animacji / na `maxValue`.
- [do uzupełnienia]

## Definition of Done ✅
- [ ] Klik rozwinięcia „Szczegóły zamówienia" → widok przewija się tak, że cała rozwinięta tabelka jest widoczna
- [ ] Klik zwinięcia → widok wraca na początek (HERO, pozycja 0)
- [ ] Manualny scroll działa normalnie przy rozwiniętej tabelce (auto-scroll nie blokuje)
- [ ] Build mobile przechodzi (`./gradlew :features:feature-participants:assembleDebug` lub odpowiedni moduł)
- [ ] Min. 1 test pokrywający zmianę (jeśli da się sensownie objąć logikę scroll/expand) lub explicit uzasadnienie czysto-UI w review note
- [ ] Review note w `review_notes/`

## Test akceptacyjny 🧪
1. Otwórz aplikację mobile, wejdź w wydarzenie → lista uczestników → otwórz kartę uczestnika (`ParticipantDetailsScreen`).
2. Kliknij nagłówek sekcji „Szczegóły zamówienia", aby ją rozwinąć.
3. Oczekiwany wynik: widok automatycznie przewija się tak, że cała rozwinięta tabelka „Szczegóły zamówienia" jest widoczna na ekranie.
4. Kliknij ponownie, aby zwinąć sekcję.
5. Oczekiwany wynik: widok automatycznie wraca na samą górę (HERO uczestnika).
6. Rozwiń sekcję ponownie i spróbuj ręcznie scrollować w górę/dół — scroll działa płynnie, bez „odbijania" do pozycji wymuszonej przez auto-scroll.

## Oczekiwany efekt wizualny 🖼️
- Po rozwinięciu „Szczegóły zamówienia": cała tabelka zamówienia widoczna w viewport (auto-scroll do sekcji).
- Po zwinięciu: ekran z HERO uczestnika u góry (scroll na pozycji 0).
- W trakcie: brak blokady manualnego scrolla.

## Kontrakt API (jeśli zmiana full-stack) 🔗
Nie dotyczy — czysta zmiana UI mobile, zero backend/API.

## Format zwrotki
- Lista zmienionych plików z jednolinijkowym opisem zmian
- Git diff summary
- Screenshot / nagranie GIF jako dowód efektu (expand → scroll, collapse → top, manual scroll)
- Wynik komendy build
- Propozycja wpisu do `decision_log.md` (jeśli podjęto decyzję)
