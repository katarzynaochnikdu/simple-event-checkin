# WO-MOB-022: Auto-scroll/focus rozwiniętej CompanyCard na ekranie „Moi podopieczni" (MyMenteesScreen)

**Data:** 2026-05-28
**Worker:** [do uzupełnienia przez Mastera]
**Stage:** [placeholder]
**Priorytet:** [placeholder — Krytyczny / Wysoki / Normalny / Niski]
**Scope:** mobile (simple-event-checkin)
**Sizing:** 🟡 mały-średni (1 plik, Compose, hoisting expanded state + LazyListState)

## Cel
Przenieść ten sam mechanizm auto-focus/auto-scroll co w WO-MOB-021 (karta uczestnika) na ekran „Moi podopieczni" (`MyMenteesScreen`). Gdy użytkownik świadomie rozwija kartę firmy (`CompanyCard`), widok ma przewinąć się tak, aby rozwinięte informacje (lista uczestników firmy) były wyfokusowane/widoczne. Auto-scroll wyłącznie jako reakcja na świadome rozwinięcie; ręczny scroll ma działać normalnie.

## Kontekst techniczny
- **Plik:** `simple-event-checkin/features/feature-dashboard/src/main/java/pl/medidesk/mobile/feature/dashboard/presentation/screen/MyMenteesScreen.kt`
- Lista firm to `LazyColumn` (**NIE** pojedynczy `Column.verticalScroll` jak w WO-MOB-021) renderujący `CompanyCard` per firma (`items(state.companies, key = { it.companyName })`, ~L475-498).
- `CompanyCard` (~L515-689) ma lokalny `var expanded by remember { mutableStateOf(false) }` (~L526); rozwinięcie pokazuje `ParticipantRow` per uczestnik (~L662-674).
- **Różnica vs WO-MOB-021:** tu jest wiele rozwijalnych kart w `LazyColumn`, więc focus = `animateScrollToItem(index rozwijanej karty)`, a NIE scroll-do-maxValue. Collapse zwykle bez akcji (lista firm — brak sensownego „powrotu do HERO").

## Zakres
[Worker MA PRAWO modyfikować:]
- `simple-event-checkin/features/feature-dashboard/src/main/java/pl/medidesk/mobile/feature/dashboard/presentation/screen/MyMenteesScreen.kt`

## Czego NIE ruszać 🛑
- Backend / API / DTO / Room — to wyłącznie zmiana UI (Compose).
- Logika `confirmCheckIn` (~L248-252) — **istniejący komentarz: `confirmCheckIn` celowo NIE woła `load()`**, żeby nie zniszczyć expanded-state `CompanyCard`. Nowy mechanizm scrolla NIE może zregresować tego zachowania.
- ParticipantDetailsScreen (zakres WO-MOB-021 — już zrobione tam, nie dotykać).

## Pliki startowe
- `MyMenteesScreen.kt` — bieżąca struktura `LazyColumn` + `CompanyCard` (lokalny `expanded`).
- `WO-MOB-021-autoscroll-szczegoly-zamowienia-participant-details.md` — wzorzec mechanizmu auto-scroll (referencja podejścia).

## Sugerowane podejście
- `rememberLazyListState()` w `MyMenteesScreen` przekazany do `LazyColumn`.
- Podnieść/zaobserwować stan `expanded` karty (np. callback `onExpandedChange(index)` lub hoisted `expandedCompanyName`) — uwaga: każda karta ma **niezależny lokalny `expanded`**, trzeba przekazać index/klucz w górę.
- Przy świadomym rozwinięciu wywołać `coroutineScope.launch { lazyListState.animateScrollToItem(index) }`, aby wyfokusować rozwiniętą kartę pod górę listy.
- Auto-scroll TYLKO przy przejściu collapsed→expanded (świadome rozwinięcie), nie przy każdej rekompozycji.

## Ryzyko
- Regresja expanded-state przy check-inie (patrz „Czego NIE ruszać" — `confirmCheckIn` bez `load()`). Mitygacja: ręczny test check-in z rozwiniętą kartą.
- Hoisting `expanded` z wielu kart — ryzyko, że rozwinięcie jednej karty zwija inne (jeśli zmienione na single-expand). Decyzja do uzgodnienia: multi-expand zachowany czy single-expand? → [do uzupełnienia]
- Auto-scroll przy każdej rekompozycji zamiast tylko na świadome rozwinięcie. Mitygacja: trigger oparty o zmianę stanu, nie o samą wartość.

## Definition of Done ✅
- [ ] Świadome rozwinięcie `CompanyCard` przewija listę tak, by rozwinięta karta była wyfokusowana/widoczna.
- [ ] Ręczny scroll listy działa normalnie (brak „wyrywania" widoku).
- [ ] Check-in uczestnika NIE zwija/nie resetuje rozwiniętej karty (zachowane zachowanie z ~L248-252).
- [ ] Build mobile przechodzi (`./gradlew :features:feature-dashboard:assembleDebug` lub odpowiedni moduł).
- [ ] Review note w `review_notes/`.

## Test akceptacyjny 🧪
1. Uruchom apkę, wejdź na ekran „Moi podopieczni".
2. Rozwiń `CompanyCard` firmy znajdującej się niżej na liście.
3. Oczekiwany wynik: widok przewija się tak, że rozwinięta karta (z listą uczestników firmy) jest wyfokusowana/widoczna.
4. Wykonaj scroll ręczny — działa normalnie, bez auto-przeskoków.
5. Z rozwiniętą kartą wykonaj check-in uczestnika → karta pozostaje rozwinięta (brak resetu expanded-state).

## Oczekiwany efekt wizualny 🖼️
- Po świadomym rozwinięciu karty firmy lista płynnie (`animateScrollToItem`) przewija się tak, że rozwinięta karta z uczestnikami jest pod górą widocznego obszaru.

## Kontrakt API (jeśli zmiana full-stack) 🔗
- Brak — zmiana wyłącznie UI (Compose), zero backend/API.

## Format zwrotki
- Lista zmienionych plików z jednolinijkowym opisem zmian.
- Git diff summary.
- Nagranie/screenshot rozwinięcia karty z auto-scrollem jako dowód.
- Wynik komendy build.
- Propozycja wpisu do `decision_log.md` (jeśli podjęto decyzję np. multi vs single-expand).
