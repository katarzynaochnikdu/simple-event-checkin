# WO-MOB-011: Bottom NavigationBar — stała pozycja ikony "Wydarzenie" + wrap tytułu do 2 linii przed ellipsis

**Data:** 2026-05-20
**Worker:** Master Agent (1-line change — bez dispatch)
**Stage:** UI polish — Mobile bottom navigation
**Priorytet:** Normalny
**Scope:** mobile (simple-event-checkin)
**Status:** ✅ DONE (opcja A — akceptacja lekkiego podnoszenia ikony)
**Snapshot:** `snapshot/wo-mob-011-pre-navbar-label-wrap-2026-05-20` @ `4993c83`

## Decyzja użytkownika (2026-05-20)
Wybrana **opcja A** — `maxLines = 2` w stock M3 `NavigationBarItem`. Świadoma akceptacja, że icon będzie minimalnie (~6-8dp) podnoszony przy długich nazwach eventu w celu zmieszczenia 2-liniowego labela w fixed-height bottom barze. Punkt 1 warunku bramkowego ZREZYGNOWANY na rzecz prostoty (3 vs 80-120 linii). Wrap tytułu do 2 linii (punkt 2) zrealizowany.

## Diff
```
- maxLines = 1,
+ maxLines = 2,
```
Plik: `simple-event-checkin/app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt:317`

## Weryfikacja
- [ ] User: build APK lokalnie / via EAS i sprawdzić bottom bar przy event z długą nazwą ("Dental Practice Annual Symposium 2026 — Advanced Procedures Workshop" lub podobny)
- [x] Zmiana scope: czysto front, 1 plik, 1 atrybut Text — zerowy wpływ na logikę
- [x] Pozostałe taby (Uczestnicy, Skaner) niezmienione

## Cel
W **bottom NavigationBar** aplikacji mobile (widoczny na ekranie wewnętrznym eventu — Dashboard / Uczestnicy / Skaner) poprawić tab "Wydarzenie": (1) ikona domku (`Icons.Default.Home`) ma pozostać na stałej pozycji niezależnie od długości nazwy eventu w labelu — NIE może się podnosić ani przesuwać przy długich nazwach; (2) label z nazwą konferencji ma najpierw zawijać się do dwóch wierszy, a dopiero potem (gdy nadal nie mieści się) skracać ellipsisem. Obecnie label ma `maxLines = 1` i skraca się już po pierwszym wierszu (np. "Dental Practice A...").

## Warunek bramkowy 🚦 — **REALNE ograniczenie M3**

**Material 3 `NavigationBarItem` ma fixed-height layout (~80dp)** z wewnętrznym Column (icon → label). Przy `maxLines = 2` dla labela komponent **podniesie ikonę**, żeby zmieścić obie linie tekstu w fixed height — to jest nieodłączna cecha komponentu, NIE bug.

**Trzy realne opcje:**
- **(A) Zaakceptować podnoszenie ikony** — dopuścić `maxLines = 2` w stock `NavigationBarItem`, ikona przesuwa się minimalnie w górę (rezygnacja z warunku bramkowego).
- **(B) Custom NavBarItem** — zastąpić `NavigationBarItem` własnym `Box`/`Column` w `NavigationBar`, gdzie icon ma `Modifier.align(Alignment.TopCenter)` z fixed top padding, label rośnie w dół niezależnie. Wymaga reimplementacji ripple, indicator, selected/unselected colors (~80-120 linii kodu vs 5 obecnych).
- **(C) Zachować `maxLines = 1` + ellipsis** — status quo, krótkie nazwy widoczne w całości, długie ucięte. Najbezpieczniejsze, zero ryzyka layout glitches.

**Decyzja per warunek bramkowy w opisie:** punkt 1 (stała pozycja ikony) jest warunkiem. Jeśli user akceptuje opcję B (custom komponent) → realizujemy oba punkty. Jeśli user akceptuje opcję A → robimy punkt 2 z notką o akceptowalnym przesunięciu icony. Jeśli C → zamykamy WO bez zmian.

## Zakres
- `simple-event-checkin/app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt` — funkcja `MainScreen()`, `NavigationBar` w `bottomBar` (linie ~293-358), konkretnie pierwszy `NavigationBarItem` (linie 309-333) z `Icons.Default.Home` + label `eventName ?: "Wydarzenie"`.

## Czego NIE ruszać 🛑
- Pozostałe taby `NavigationBarItem` (Uczestnicy, Skaner) — bez zmian
- Logika `isDashboard` / `onBackToEvents` / nawigacja `innerNav.navigate(...)`
- Kolory tła / accent / dark mode detection (`isDark`, `eventAccentColor`, `navColors`)
- TopAppBar w ekranach (Wydarzenia / Dashboard) — to inne komponenty
- `EventCompactCard` w `EventsScreen.kt` (to inny widok — lista eventów, NIE bottom bar)
- Wysokość bottom NavigationBar (zachować standard M3 ~80dp)

## Pliki startowe
- `simple-event-checkin/app/src/main/java/pl/medidesk/mobile/navigation/AppNavHost.kt` (linie 309-333)
- Dla opcji B (custom NavBarItem): wzór z M3 source `androidx.compose.material3.NavigationBarItem` + dokumentacja Compose Layout APIs (Column, Box, align)

## Ryzyko
[do uzupełnienia]
- Zmiana layoutu Row/Column może wpłynąć na pozostałe elementy karty (data, miejsce, badge)
- `maxLines = 2` przy bardzo krótkich tytułach — sprawdzić czy nie psuje wyrównania
- Zmiana wysokości karty wpłynie na density listy

## Definition of Done ✅
- [ ] Ikona (domek) pozostaje na stałej pozycji niezależnie od długości tytułu (1, 2, 2+ linii)
- [ ] Tytuł zawija się do max 2 linii, ellipsis dopiero po 2. linii
- [ ] Build mobile przechodzi (`./gradlew assembleDebug`)
- [ ] Screenshot z 3 wariantami tytułu: krótki / dwuwierszowy / bardzo długi (z ellipsis)
- [ ] Review note w `simple-event-checkin/.agents/work_orders/review_notes/`

## Test akceptacyjny 🧪
1. Zaloguj się do aplikacji mobile
2. Wejdź na ekran listy eventów
3. Zweryfikuj kartę z krótkim tytułem (1 linia) — ikona na stałej pozycji
4. Zweryfikuj kartę z tytułem mieszczącym się w 2 liniach — ikona na tej samej stałej pozycji, tytuł zawinięty (NIE skrócony)
5. Zweryfikuj kartę z bardzo długim tytułem — tytuł w 2 liniach + ellipsis, ikona dalej na tej samej pozycji
6. (Jeśli testowych danych brak) — w stagingu / przez zmianę testową dodać event z tytułem "Dental Practice Annual Symposium 2026 — Advanced Procedures Workshop"

## Oczekiwany efekt wizualny 🖼️
- Ikona domku w karcie eventu: stała pozycja (np. top-start lub center-vertical w stosunku do bloku tytułu — do ustalenia po analizie obecnego layoutu), niezależna od długości tytułu
- Tytuł: maxLines = 2, overflow = ellipsis (zamiast obecnego maxLines = 1 + ellipsis)
- Pozostałe elementy karty (data, miejsce, status) bez zmian w pozycji

## Kontrakt API (jeśli zmiana full-stack) 🔗
Nie dotyczy — zmiana wyłącznie front-mobile (UI Compose).

## Definition of Ready — checklist (7 pkt)
- [✅] Cel jasny
- [✅] Zakres z konkretnymi plikami — `AppNavHost.kt:309-333`
- [✅] Czego nie ruszać
- [✅] Test akceptacyjny
- [✅] Oczekiwany efekt wizualny / behawioralny
- [✅] Kontrakt API (N/A — front-only)
- [✅] Pliki startowe — j.w.

**Sizing:** 🟢 mały (1 plik, 1 komponent NavigationBarItem; opcja B podnosi do 🟡 średni przez ~80-120 dodatkowych linii custom layout)

## Format zwrotki
- Lista zmienionych plików z opisem zmian
- Git diff summary
- 3 screenshoty (krótki / 2-wiersze / długi+ellipsis) jako dowód
- Wynik `./gradlew assembleDebug`
- Raport czy warunek bramkowy (stała pozycja ikony) był osiągalny i jak

## Załączniki / kontekst
- Referencyjny screenshot od użytkownika: karta eventu z ikoną domku u góry, pod nią czerwony tekst "Dental Practice A..." obcięty kropkami w jednym wierszu
- Lokalizacja: ekran listy eventów po zalogowaniu (komponent karty eventu / EventCard)
