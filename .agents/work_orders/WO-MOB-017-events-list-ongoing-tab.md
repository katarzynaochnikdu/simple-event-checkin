# WO-MOB-017: Zakładka „Trwające" na liście wydarzeń + fix „dzień wydarzenia wpada w przeszłość"

**Status:** ✅ DONE (2026-05-28) — zaimplementowane, build `assembleDebug` PASS, czeka na on-device QA + commit/push (user go-ahead).
**Data:** 2026-05-28
**Worker:** Implementer (mobile / Kotlin)
**Stage:** Mobile — Events list UX
**Priorytet:** Wysoki
**Scope:** mobile (`simple-event-checkin/`)

## Cel
Na ekranie listy wydarzeń (`EventsScreen`) dodać nową, **pierwszą od lewej** zakładkę **„Trwające"** — widoczną **tylko gdy istnieje ≥1 wydarzenie aktualnie trwające** (po dacie). Przy okazji naprawić błąd klasyfikacji: dziś w **dniu wydarzenia** event natychmiast „wpada w przeszłość" (bo `parseToDateTime` dla daty bez godziny zwraca 00:00, więc `endDate.isBefore(now)` jest prawdą już o 00:01). Docelowe zakładki:

**Trwające | Nadchodzące | Przeszłe | Sandbox** — przy czym **Sandbox widoczny tylko dla deweloperów** (`BuildConfig.DEBUG`).

## Zakres (pliki, które Worker MA PRAWO modyfikować)
- `features/feature-events/src/main/java/pl/medidesk/mobile/feature/events/presentation/viewmodel/EventsViewModel.kt` — enum `EventTab`, klasyfikacja dat (data-granular), `visibleTabs`, domyślna zakładka.
- `features/feature-events/src/main/java/pl/medidesk/mobile/feature/events/presentation/screen/EventsScreen.kt` — render `visibleTabs` zamiast `EventTab.entries`, label „Trwające", mapowanie `selectedTabIndex`.
- `features/feature-events/build.gradle.kts` — dodać `buildFeatures { buildConfig = true }` (potrzebne do `BuildConfig.DEBUG` gating Sandbox; wzorzec 1:1 z `feature-dashboard/build.gradle.kts`).

## Czego NIE ruszać 🛑
- `core/core-model/.../EventItem.kt` — **bez zmian schematu** (klasyfikacja jest po stronie klienta; pola `startDate`/`endDate` już są).
- `backend/api/mobile.py` oraz jakikolwiek endpoint — **brak zmian API** (to czysto kliencka logika prezentacji).
- `parseToDateTime` / `formatDateLabel` w `EventsScreen.kt` — reuse as-is (nie zmieniać parsowania).
- Inne ekrany / moduły mobilne.
- `feature-sponsors`, `feature-walkin`, `feature-inhub` (wyłączone w `settings.gradle.kts`).

## Pliki startowe
- `EventsViewModel.kt` (linie 16, 43-117) — `enum EventTab`, `combine{}`, `isSandbox`, `isPast`.
- `EventsScreen.kt` (linie 71-86) — `TabRow` + `EventTab.entries.forEach` + `selectedTabIndex = uiState.selectedTab.ordinal`.
- `feature-dashboard/build.gradle.kts` (linie 5-10) — wzorzec `buildFeatures { buildConfig = true }`.
- `MyMenteesScreen.kt:125` + `AppNavHost.kt:291` — istniejący wzorzec dev-gating przez `BuildConfig.DEBUG`.

## Decyzje projektowe (DO POTWIERDZENIA przez usera — domyślne propozycje)

1. **„Trwające" = klasyfikacja data-granularna** *(rekomendacja)*. Wydarzenie jest „Trwające" gdy `dziś ∈ [dzień startu .. dzień końca]` włącznie — **ignorując godziny**. Skutki:
   - W dniu wydarzenia event jest „Trwające" przez **cały dzień** (nie wpada w przeszłość o 00:01 ani po godzinie końcowej). ← to naprawia zgłoszony błąd.
   - Event zaczynający się dziś o 18:00 jest „Trwające" już dziś (nie „Nadchodzące").
   - Event który skończył się wczoraj → „Przeszłe".
   - *Alternatywa (odrzucona):* czas-precyzyjny (`now ∈ [start, end]`) — ale wtedy po godzinie końcowej event znów „wpada w przeszłość" w dniu wydarzenia (dokładnie to, na co user narzeka).

2. **Domyślnie wybrana zakładka po wejściu** *(rekomendacja)*: **„Trwające" gdy istnieje ≥1 trwające wydarzenie, inaczej „Nadchodzące"**. Pozycja „Trwające" zawsze skrajnie z lewej (gdy widoczna). Po ręcznym kliknięciu innej zakładki — wybór usera wygrywa.

3. **Gating „Sandbox" = `BuildConfig.DEBUG`** *(rekomendacja)*. Zakładka Sandbox widoczna **tylko w buildach debug** (deweloperzy). W APK preview/release **nikt** jej nie widzi (też organizatorzy). Uzasadnienie: backend zna tylko role `participant`/`organizer` — „deweloper" to nie rola serwerowa; `BuildConfig.DEBUG` to ustalony w projekcie wzorzec dev-only UI (`MyMenteesScreen`, `AppNavHost`).
   - *Alternatywa:* gating po `user.role`/koncie — odrzucona (brak roli „developer" w backendzie).

4. **Test regresji**: projekt mobilny **nie ma jeszcze żadnej infrastruktury testowej** (`src/test` nie istnieje w żadnym module). Propozycja: w tym WO **wyodrębnić czyste, deterministyczne funkcje klasyfikacji** (`isOngoing/isUpcoming/isPast(event, today: LocalDate)`) — gotowe pod test — a faktyczny harness JUnit + test `test_bug_event_day_not_past` zrobić **osobnym WO** (pierwsze uruchomienie test infra mobile to oddzielna inicjatywa). Weryfikacja w tym WO = **manualne QA na zbudowanym APK**. ← do akceptacji (zgodne z fazowym wdrożeniem testów i „NIE 100% na siłę").

## Ryzyko
- **Mapowanie indeksu zakładki**: obecnie `selectedTabIndex = uiState.selectedTab.ordinal`. Przy dynamicznej widoczności zakładek ordinal ≠ pozycja widoczna → trzeba użyć `visibleTabs.indexOf(effectiveTab)`. Mitygacja: `coerceAtLeast(0)` + `effectiveTab` zawsze ∈ `visibleTabs`.
- **`selectedTab` poza `visibleTabs`** (np. user był na „Trwające", event się skończył, zakładka znika): mitygacja — `effectiveTab = selectedTab.takeIf { it in visibleTabs } ?: defaultTab`.
- **Stabilność zakładek podczas wyszukiwania**: `anyOngoing` liczone z `_rawEvents` (NIE z listy przefiltrowanej po search) — zakładka „Trwające" nie znika/pojawia się przy wpisywaniu w search. Mitygacja w designie.
- **Daty nieparsowalne**: `parseToDateTime` zwraca `now()` dla pustych/błędnych — taki event trafi do „Trwające". Ryzyko pre-istniejące, akceptowalne, do odnotowania.
- **Gradle `buildConfig=true`**: minimalne; 1:1 z feature-dashboard. Build APK jako weryfikacja.

## Definition of Done ✅
- [ ] `EventTab` rozszerzony o `ONGOING` (kolejność: `ONGOING, UPCOMING, PAST, SANDBOX`).
- [ ] Klasyfikacja data-granularna: `isOngoing` = `dzień startu ≤ dziś ≤ dzień końca`; `isUpcoming` = `dziś < dzień startu`; `isPast` = `dziś > dzień końca`. Wyodrębnione jako czyste funkcje przyjmujące `today: LocalDate`.
- [ ] `EventsUiState.visibleTabs: List<EventTab>` — `ONGOING` tylko gdy `anyOngoing`; `SANDBOX` tylko gdy `BuildConfig.DEBUG`; `UPCOMING`+`PAST` zawsze.
- [ ] Domyślna zakładka = `ONGOING` gdy `anyOngoing`, inaczej `UPCOMING`; wybór usera respektowany.
- [ ] `EventsScreen` renderuje `uiState.visibleTabs` (nie `EventTab.entries`); label `ONGOING` → „Trwające"; `selectedTabIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)`.
- [ ] `feature-events/build.gradle.kts` ma `buildFeatures { buildConfig = true }`.
- [ ] Build: `./gradlew :features:feature-events:assembleDebug` (lub `assembleDebug` całości) PRZECHODZI.
- [ ] Reguły bezpieczeństwa zachowane (brak nowych endpointów/PII/auth — N/A poza weryfikacją że nic nie wyciekło).
- [ ] Review note w `review_notes/REVIEW-WO-MOB-017.md`.

## Test akceptacyjny 🧪 (manualne QA na APK — brak harness JUnit w mobile)
1. Zbuduj i zainstaluj debug APK (`./gradlew assembleDebug` + `adb install`), zaloguj się (konto z dostępem do eventów).
2. **Scenariusz „dzień wydarzenia"**: upewnij się, że istnieje event, którego data startu/końca = **dzisiaj** (2026-05-28).
   - Oczekiwane: zakładka **„Trwające"** jest **pierwsza od lewej**, **domyślnie zaznaczona**, a event widnieje w niej (NIE w „Przeszłe").
3. **Scenariusz „brak trwających"**: gdy żaden event nie trwa dziś.
   - Oczekiwane: zakładki to **Nadchodzące | Przeszłe** (+ Sandbox jeśli debug). „Trwające" **nie istnieje**; domyślnie „Nadchodzące".
4. **Nadchodzące**: event z datą startu > dziś → zakładka „Nadchodzące".
5. **Przeszłe**: event z datą końca < dziś → zakładka „Przeszłe" (sort malejąco).
6. **Search**: wpisz frazę — zakładki nie migoczą/nie zmieniają liczby; filtruje listę w obrębie aktywnej zakładki.
7. **Sandbox / dev-gating**: w debug APK zakładka „Sandbox" widoczna; (weryfikacja koncepcyjna) w release/preview byłaby ukryta.
8. Brak crashy w logach (`adb logcat`), brak pustego stanu gdy są eventy.

## Oczekiwany efekt wizualny 🖼️
- `TabRow` na górze listy wydarzeń pokazuje **„Trwające"** jako pierwszą zakładkę **tylko gdy** coś trwa; po niej „Nadchodzące", „Przeszłe", (i „Sandbox" w debug).
- W dniu wydarzenia event pojawia się pod „Trwające" (wcześniej błędnie pod „Przeszłe").
- Gdy nic nie trwa — układ jak dziś, ale bez zakładki „Trwające".

## Kontrakt API
**N/A** — zmiana czysto kliencka (prezentacja). Zero zmian w `mobile.py`, zero zmian w `EventItem`/DTO. `GET /api/mobile/events` zwraca już `startDate`/`endDate` (oraz `start_date`/`end_date` w surowym JSON) — wystarcza.

## Format zwrotki
- Lista zmienionych plików z 1-linijkowym opisem.
- `git diff --stat` (submoduł `simple-event-checkin`).
- Wynik build (`assembleDebug` PASS/FAIL) + ewentualny screenshot z urządzenia/emulatora.
- Propozycja wpisu do `decision_log.md` (klasyfikacja data-granularna + dev-gating Sandbox).
- Propozycja gotcha (parseToDateTime → now() dla błędnych dat trafia do „Trwające").

---

## Sizing
🟡 **Średni** — 3 pliki, 1 warstwa (presentation) + drobny gradle toggle; logika dynamicznych zakładek + domyślny wybór + dev-gating + build APK jako weryfikacja.

## Snapshot (Step 2.5)
**WYMAGANY** — zmiana w kodzie aplikacji mobilnej (`simple-event-checkin/`). Tag proponowany: `pre-mobile-events-ongoing-tab-2026-05-28`.
